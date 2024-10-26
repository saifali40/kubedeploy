package in.saifali.crds.kubedeployer;

import io.fabric8.openshift.api.model.clusterautoscaling.v1.ResourceLimits;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class KubeDeployerSpec{
    private String DeploymentName;
    private String DeploymentVersion;
    private int replicas;
    private List<EnvVar> env;
    private ResourceLimits resources;
    private Map<String, String> config;
    private StorageConfig storage;
    private IngressConfig ingress;
    private Integer targetPort;
    private Integer port;
}
