# QPay EC2 deployment

Use an Ubuntu EC2 instance with Docker Engine, the Compose plugin, Git, and curl installed. For a single-host demonstration, allow inbound TCP 80/443 only; do not expose MySQL, MongoDB, Redis, or Kafka.

## Configure

```bash
git clone <repository-url> qpay
cd qpay
cp deploy/ec2/.env.example deploy/ec2/.env
chmod 600 deploy/ec2/.env
editor deploy/ec2/.env
```

Replace every placeholder. Keep `deploy/ec2/.env` off Git and use AWS Secrets Manager or SSM Parameter Store for a production rollout. Terminate TLS at an Application Load Balancer or reverse proxy and set `WEBSOCKET_ALLOWED_ORIGINS` to the public HTTPS origin.

## Deploy

```bash
chmod +x deploy/ec2/deploy.sh
./deploy/ec2/deploy.sh
```

Flyway initializes each service database automatically. Persistent data is stored in named Docker volumes. Inspect status with:

```bash
docker compose --env-file deploy/ec2/.env -f deploy/ec2/compose.yml ps
docker compose --env-file deploy/ec2/.env -f deploy/ec2/compose.yml logs -f api-gateway
```

The single-host Compose layout is intended for demonstrations and lower environments. A production payment deployment should move stateful systems to managed services, use multiple availability zones, ship logs and metrics centrally, configure backups, and run multiple stateless service replicas behind a load balancer.
