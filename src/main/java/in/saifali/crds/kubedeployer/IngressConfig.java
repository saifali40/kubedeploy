package in.saifali.crds.kubedeployer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngressConfig {
    private boolean enabled;
    private String host;
    private String path;
    private String pathType;
    private boolean tlsEnabled;
    private String tlsSecretName;
}
