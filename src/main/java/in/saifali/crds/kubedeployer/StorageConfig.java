package in.saifali.crds.kubedeployer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorageConfig {
    private String size;
    private String storageClassName;
    private String accessMode;
    private String mountPath;
}
