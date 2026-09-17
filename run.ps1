# Camunda 8 SaaS Cluster Connection Script
# Cluster: 456d1d4f-ccc8-40ca-b157-0a96eeada22c (jfk-1)

$env:ZEEBE_ADDRESS                    = '456d1d4f-ccc8-40ca-b157-0a96eeada22c.jfk-1.zeebe.camunda.io:443'
$env:ZEEBE_CLIENT_ID                  = 'uC.g_IRQtJOm5IoX71XBMQVLtWXp0HhB'
$env:ZEEBE_CLIENT_SECRET              = 'pIzaJQ7F_-Biejj1bs0Z-~acFHCQhpzrzdDxHpULcM1Gs~0lCae-jXBZPihnDuNz'
$env:ZEEBE_AUTHORIZATION_SERVER_URL   = 'https://login.cloud.camunda.io/oauth/token'
$env:ZEEBE_REST_ADDRESS               = 'https://jfk-1.api.camunda.io/456d1d4f-ccc8-40ca-b157-0a96eeada22c'
$env:ZEEBE_GRPC_ADDRESS               = 'grpcs://456d1d4f-ccc8-40ca-b157-0a96eeada22c.jfk-1.zeebe.camunda.io:443'
$env:ZEEBE_TOKEN_AUDIENCE             = 'zeebe.camunda.io'
$env:CAMUNDA_CLUSTER_ID               = '456d1d4f-ccc8-40ca-b157-0a96eeada22c'
$env:CAMUNDA_CLIENT_ID                = 'uC.g_IRQtJOm5IoX71XBMQVLtWXp0HhB'
$env:CAMUNDA_CLIENT_SECRET            = 'pIzaJQ7F_-Biejj1bs0Z-~acFHCQhpzrzdDxHpULcM1Gs~0lCae-jXBZPihnDuNz'
$env:CAMUNDA_CLUSTER_REGION           = 'jfk-1'
$env:CAMUNDA_OAUTH_URL                = 'https://login.cloud.camunda.io/oauth/token'
$env:CAMUNDA_CLIENT_MODE              = 'saas'

Write-Host "Camunda SaaS environment variables set." -ForegroundColor Green
Write-Host "Starting application (compile + exec)..." -ForegroundColor Cyan

mvn compile exec:java
