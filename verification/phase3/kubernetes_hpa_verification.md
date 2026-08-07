# Kubernetes HPA Verification

## Result

PASS

## Configuration

- Metric: CPU utilization
- Target utilization: 60%
- Minimum replicas: 3
- Maximum replicas: 8
- Scale-down stabilization window: 120 seconds
- Scale-down limit: 25% per 60 seconds

## Scale-out result

Baseline replicas:

- 3

Observed scaling:

- 3 -> 6 -> 8

Peak observed CPU utilization:

- 172% relative to CPU request

Maximum replica enforcement:

- 8 replicas
- HPA reported ScalingLimited=True when calculated demand exceeded maxReplicas

All scaled replicas became Ready.

## Scale-down result

After the synthetic load was removed:

- 8 -> 6 -> 4 -> 3

Final state:

- Deployment READY: 3/3
- HPA replicas: 3
- CPU returned to approximately 12-17%
- No restart storm or failed workload recovery observed

## Verified property

The Kubernetes HorizontalPodAutoscaler successfully scaled the
Spring Boot workload out under CPU pressure and automatically returned
the deployment to its minimum replica count after load removal.

This verifies CPU-based horizontal autoscaling under the tested
single-node Kubernetes environment.
