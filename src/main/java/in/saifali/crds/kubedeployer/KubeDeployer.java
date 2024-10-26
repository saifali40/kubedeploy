package in.saifali.crds.kubedeployer;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("in.saifali")
@Version("v1alpha1")
@ShortNames({"kd","kdeployer","kubedeployer"})
public class KubeDeployer extends CustomResource<KubeDeployerSpec,KubeDeployerStatus> implements Namespaced {
    @Override
    protected KubeDeployerSpec initSpec() {
        return new KubeDeployerSpec();
    }
}
