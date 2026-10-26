// DockMind CI/CD pipeline.
//
// Every build: backend tests (Maven), web app build and tests, infrastructure check (terraform validate).
// Optional, off by default and gated:
//   PUBLISH_IMAGES - build arm64 images and push them to ECR, tagged with the Git commit
//   DEPLOY         - terraform plan, then a person must approve before terraform apply
// AWS credentials come from the Jenkins credential "aws-dockmind" (never from the repository).
pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        timeout(time: 60, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    parameters {
        booleanParam(name: 'PUBLISH_IMAGES', defaultValue: false, description: 'Build images and push them to ECR')
        booleanParam(name: 'DEPLOY', defaultValue: false, description: 'Plan and (after approval) apply Terraform')
        string(name: 'AWS_REGION', defaultValue: 'us-east-1', description: 'AWS region')
    }

    environment {
        IMAGE_TAG = "${env.GIT_COMMIT ? env.GIT_COMMIT.take(12) : 'local'}"
    }

    stages {
        stage('Backend: build and test') {
            steps {
                sh './mvnw -B verify'
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: '**/target/surefire-reports/*.xml'
                }
            }
        }

        stage('Web app: build and test') {
            steps {
                dir('frontend') {
                    sh 'npm ci --no-audit --no-fund'
                    sh 'npm run build'
                    sh 'npm test'
                }
            }
        }

        stage('Infrastructure: validate') {
            steps {
                sh './scripts/validate-infra.sh'
            }
        }

        stage('Images: build and push to ECR') {
            when { expression { params.PUBLISH_IMAGES } }
            steps {
                withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', credentialsId: 'aws-dockmind']]) {
                    sh '''
                        ACCOUNT=$(aws sts get-caller-identity --query Account --output text)
                        REGISTRY="$ACCOUNT.dkr.ecr.$AWS_REGION.amazonaws.com"
                        aws ecr get-login-password --region "$AWS_REGION" | docker login --username AWS --password-stdin "$REGISTRY"
                        ./scripts/build-images.sh "$IMAGE_TAG" "$REGISTRY"
                    '''
                }
            }
        }

        stage('Deploy: plan') {
            when { expression { params.DEPLOY } }
            steps {
                withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', credentialsId: 'aws-dockmind']]) {
                    dir('infra/terraform') {
                        sh 'terraform init -input=false'
                        sh 'terraform plan -input=false -out=tfplan -var "image_tag=$IMAGE_TAG" -var "aws_region=$AWS_REGION"'
                    }
                }
            }
        }

        stage('Deploy: approve') {
            when { expression { params.DEPLOY } }
            steps {
                timeout(time: 30, unit: 'MINUTES') {
                    input message: 'Apply this plan to AWS? This creates or changes paid resources.', ok: 'Apply'
                }
            }
        }

        stage('Deploy: apply and verify') {
            when { expression { params.DEPLOY } }
            steps {
                withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', credentialsId: 'aws-dockmind']]) {
                    dir('infra/terraform') {
                        sh 'terraform apply -input=false tfplan'
                        sh '''
                            URL=$(terraform output -raw app_url)
                            for i in $(seq 1 60); do
                              if curl -fs -o /dev/null "$URL/healthz" && curl -fs -o /dev/null "$URL/realms/dockmind"; then
                                echo "Deployed and healthy: $URL"; exit 0
                              fi
                              sleep 10
                            done
                            echo "Deployment did not become healthy at $URL"; exit 1
                        '''
                    }
                }
            }
        }
    }

    post {
        always {
            archiveArtifacts allowEmptyArchive: true, artifacts: 'docs/benchmarks/**'
        }
    }
}
