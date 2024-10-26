package in.saifali.controller;

import in.saifali.crds.kubedeployer.KubeDeployer;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;
import io.fabric8.kubernetes.client.dsl.ServiceResource;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ControllerConfiguration
public class KubeDeployerController implements Reconciler<KubeDeployer> {
    private final KubernetesClient client = new DefaultKubernetesClient();

    @Override
    public UpdateControl<KubeDeployer> reconcile(KubeDeployer kubeDeployer, Context<KubeDeployer> context) throws Exception {
        Deployment deployment = createOrUpdateDeployment(kubeDeployer);
        if (deployment == null) return UpdateControl.noUpdate();

        Service service = createOrUpdateService(kubeDeployer);
        if (service == null) return UpdateControl.noUpdate();

        if (kubeDeployer.getSpec().getIngress().isEnabled()) {
            Ingress ingress = createOrUpdateIngress(kubeDeployer);
            if (ingress == null) return UpdateControl.noUpdate();
        }

        return UpdateControl.updateResource(kubeDeployer);
    }

    private ObjectMeta createMetadata(KubeDeployer kubeDeployer, String nameSuffix) {
        return new ObjectMetaBuilder()
                .withName(kubeDeployer.getSpec().getDeploymentName() + nameSuffix)
                .withNamespace(kubeDeployer.getMetadata().getNamespace())
                .addToLabels("app", kubeDeployer.getSpec().getDeploymentName())
                .addToOwnerReferences(createOwnerReference(kubeDeployer))
                .addToAnnotations("traefik.ingress.kubernetes.io/router.entrypoints", "web")
                .build();
    }

    private int determinePort(KubeDeployer kubeDeployer, boolean targetPort) {
        Integer port = targetPort ? kubeDeployer.getSpec().getTargetPort() : kubeDeployer.getSpec().getPort();
        return port != null ? port : 80;
    }

    private OwnerReference createOwnerReference(KubeDeployer kubeDeployer) {
        return new OwnerReferenceBuilder()
                .withApiVersion(kubeDeployer.getApiVersion())
                .withKind(kubeDeployer.getKind())
                .withName(kubeDeployer.getMetadata().getName())
                .withUid(kubeDeployer.getMetadata().getUid())
                .withController(true)
                .withBlockOwnerDeletion(true)
                .build();
    }

    private Deployment createOrUpdateDeployment(KubeDeployer kubeDeployer) {
        Deployment deployment = new DeploymentBuilder()
                .withMetadata(createMetadata(kubeDeployer, ""))
                .withNewSpec()
                .withReplicas(kubeDeployer.getSpec().getReplicas())
                .withNewSelector()
                .addToMatchLabels("app", kubeDeployer.getSpec().getDeploymentName())
                .endSelector()
                .withNewTemplate()
                .withNewMetadata()
                .addToLabels("app", kubeDeployer.getSpec().getDeploymentName())
                .endMetadata()
                .withNewSpec()
                .addNewContainer()
                .withName(kubeDeployer.getSpec().getDeploymentName())
                .withImage(kubeDeployer.getSpec().getDeploymentVersion())
                .addAllToEnv(getEnvironmentVariables(kubeDeployer))
                .addNewPort().withContainerPort(determinePort(kubeDeployer, true)).endPort()
                .withResources(createResourceRequirements(kubeDeployer))
                .endContainer()
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();

        Deployment currentDeployment = client.apps().deployments().inNamespace(deployment.getMetadata().getNamespace()).withName(deployment.getMetadata().getName()).get();
        RollableScalableResource<Deployment> deploymentResource = client.apps().deployments()
                .inNamespace(kubeDeployer.getMetadata().getNamespace())
                .resource(deployment);

        return currentDeployment==null ? deploymentResource.create() : deploymentResource.update();
    }

    private Service createOrUpdateService(KubeDeployer kubeDeployer) {
        Service service = new ServiceBuilder()
                .withMetadata(createMetadata(kubeDeployer, "-service"))
                .withNewSpec()
                .addNewPort()
                .withPort(determinePort(kubeDeployer, false))
                .withTargetPort(new IntOrString(determinePort(kubeDeployer, true)))
                .endPort()
                .withSelector(Collections.singletonMap("app", kubeDeployer.getSpec().getDeploymentName()))
                .withType("ClusterIP")
                .endSpec()
                .build();

        Service currentService = client.services().inNamespace(service.getMetadata().getNamespace()).withName(service.getMetadata().getName()).get();
        ServiceResource<Service> serviceResource = client.services()
                .inNamespace(kubeDeployer.getMetadata().getNamespace()).resource(service);

        return currentService == null? serviceResource.create() : serviceResource.update();
    }

    private Ingress createOrUpdateIngress(KubeDeployer kubeDeployer) {
        Ingress ingress = new IngressBuilder()
                .withNewMetadata()
                .withName(kubeDeployer.getSpec().getDeploymentName()+"-ingress")
                .withNamespace("default")
                .addToAnnotations("traefik.ingress.kubernetes.io/router.entrypoints", "web")
                .addToOwnerReferences(createOwnerReference(kubeDeployer))
                .endMetadata()
                .withNewSpec()
                .withIngressClassName("traefik")
                .addNewRule()
                .withHost(kubeDeployer.getSpec().getIngress().getHost())
                .withNewHttp()
                .addNewPath()
                .withPath("/")
                .withPathType("Prefix")
                .withNewBackend()
                .withNewService()
                .withName(kubeDeployer.getSpec().getDeploymentName() + "-service")
                .withNewPort().withNumber(determinePort(kubeDeployer, false)).endPort()
                .endService()
                .endBackend()
                .endPath()
                .endHttp()
                .endRule()
                .endSpec()
                .build();

        Ingress currentIngress = client.network().v1().ingresses().inNamespace(ingress.getMetadata().getNamespace()).withName(ingress.getMetadata().getName()).get();

        Resource<Ingress> ingressResource = client.network().v1().ingresses()
                .inNamespace(kubeDeployer.getMetadata().getNamespace())
                .resource(ingress);


        return currentIngress == null ? ingressResource.create() : ingressResource.update();
    }

    private List<EnvVar> getEnvironmentVariables(KubeDeployer kubeDeployer) {
        return kubeDeployer.getSpec().getEnv() == null
                ? Collections.emptyList()
                : kubeDeployer.getSpec().getEnv().stream()
                .map(env -> new EnvVar(env.getName(), env.getValue(), null))
                .collect(Collectors.toList());
    }

    private ResourceRequirements createResourceRequirements(KubeDeployer kubeDeployer) {
        return new ResourceRequirementsBuilder()
                .addToLimits(Map.of(
                        "memory", new Quantity(kubeDeployer.getSpec().getResources().getMemory().getMax() + "Mi"),
                        "cpu", new Quantity(String.valueOf(kubeDeployer.getSpec().getResources().getCores().getMax()))
                ))
                .addToRequests(Map.of(
                        "memory", new Quantity(kubeDeployer.getSpec().getResources().getMemory().getMin() + "Mi"),
                        "cpu", new Quantity(String.valueOf(kubeDeployer.getSpec().getResources().getCores().getMin()))
                ))
                .build();
    }
}
