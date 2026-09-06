# LedgerFlow — AWS Terraform

Provisions LedgerFlow on **ECS Fargate + RDS Postgres + MSK Serverless**, with the
app's ECS task role scoped to call **AWS Bedrock** for the LLM risk analyst. This is
scaffolding: it is authored to be reviewed and applied by you, against your own AWS
account. Nothing here has been applied on your behalf.

## What this provisions (and what it costs)

- A VPC (2 public + 2 private subnets, 1 NAT gateway)
- Two ECR repositories (`ledgerflow-app`, `ledgerflow-fraud-service`)
- RDS Postgres 16 (private, encrypted, `db.t4g.micro` by default)
- MSK Serverless (auto-scaling Kafka, IAM-authenticated)
- An ECS Fargate cluster with two services (app — public via ALB; fraud-service —
  internal only) and Cloud Map service discovery so the app reaches fraud-service by
  internal DNS, mirroring the docker-compose setup
- IAM roles, an Application Load Balancer, and CloudWatch log groups

**This is billable infrastructure** — RDS, MSK Serverless, the NAT gateway, and the
ALB all cost money continuously once applied, independent of traffic. Review
`variables.tf` and run `terraform plan` before `apply`, and see **Teardown** below
when you're done.

## Prerequisites

1. An AWS account with billing enabled, and the AWS CLI configured
   (`aws configure`) with credentials that can create VPC/ECS/RDS/MSK/IAM/Bedrock
   resources.
2. [Terraform](https://developer.hashicorp.com/terraform/downloads) >= 1.7.
3. **Manual, console-only step Terraform cannot do for you:** enable model access
   for the Claude model you intend to use, in the **Bedrock console → Model
   access**, in the *same region* you deploy to (`var.aws_region`). Bedrock model
   entitlement is an account-level grant, not a Terraform-managed resource — the
   app's IAM permissions will be correct, but calls will still fail with an
   access-denied error until this is done in the console.
4. (Optional but recommended) An S3 bucket + DynamoDB table for remote state — fill
   in the commented `backend "s3"` block in `versions.tf`. Without it, state is
   local to your machine, which is fine for a first pass but not for a team.

## Deploy

```bash
cd infra/terraform
terraform init
terraform plan -out=tfplan
terraform apply tfplan
```

Review the plan output carefully — it lists every resource before anything is
created.

## Publishing images to ECR

The task definitions reference `<repo-url>:<tag>` (default tag `latest`, see
`app_image_tag`/`fraud_service_image_tag` in `variables.tf`). Get an image into ECR
before the ECS services can start successfully.

**Manual (fastest way to get started):**

```bash
aws ecr get-login-password --region <region> | \
  docker login --username AWS --password-stdin <account-id>.dkr.ecr.<region>.amazonaws.com

docker build -t <account-id>.dkr.ecr.<region>.amazonaws.com/ledgerflow-app:latest .
docker push <account-id>.dkr.ecr.<region>.amazonaws.com/ledgerflow-app:latest

docker build -t <account-id>.dkr.ecr.<region>.amazonaws.com/ledgerflow-fraud-service:latest ./fraud-service
docker push <account-id>.dkr.ecr.<region>.amazonaws.com/ledgerflow-fraud-service:latest
```

(`<account-id>` and `<region>` are printed by `terraform output`.)

**Recommended for ongoing use:** `.github/workflows/build-and-push.yml` at the repo
root builds and pushes both images on every push to `main`, authenticating via
OIDC role assumption (no long-lived AWS keys stored as GitHub secrets). You still
need to create the IAM role it assumes and add its ARN as a repo variable — see the
comments at the top of that workflow file.

After pushing a new image, force a redeploy:

```bash
aws ecs update-service --cluster ledgerflow-cluster --service ledgerflow-app --force-new-deployment
```

## MSK Serverless client auth note

MSK Serverless requires IAM-based SASL authentication rather than the plaintext
listener docker-compose uses locally. If you deploy with `EVENTS_TRANSPORT=kafka`
against this MSK cluster, the app's classpath and Kafka client config need the
`software.amazon.msk:aws-msk-iam-auth` library and the matching
`sasl.jaas.config`/`sasl.client.callback.handler.class` properties — this is not
yet wired into `application.yml`, since local dev only ever needs plaintext Kafka.
Add it alongside your first real deploy.

## Teardown

```bash
terraform destroy
```

`aws_db_instance.main` has `deletion_protection = true` and takes a final snapshot
on deletion — you'll need to disable deletion protection (or set
`skip_final_snapshot = true` deliberately) before `destroy` will succeed, and you'll
be left with a final RDS snapshot billed at standard snapshot storage rates unless
you delete it separately.
