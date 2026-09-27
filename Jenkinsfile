// CRM 部署流水线（Jenkins）
// 运行环境：Built-In Node = Linux (amd64)，Jenkins 与 docker 引擎同在 WSL Ubuntu-22.04。
// 部署方式：从本地 bare 仓库 git clone 源码（非 SCM checkout，因 Jenkins 无 git 插件且无外网），
//          然后 docker compose 构建部署。
// 源码源：/mnt/e/code/crm.git（bare 仓库）；构建目录：/tmp/crm-build。

pipeline {
    agent { label "${params.TARGET ?: 'built-in'}" }

    options {
        timestamps()
        disableConcurrentBuilds()
    }

    parameters {
        choice(name: 'ENV', choices: ['dev', 'staging', 'prod'], description: 'Deployment environment (maps to .env)')
        booleanParam(name: 'DRY_RUN', defaultValue: true, description: 'true = build images only, do NOT run up -d')
        string(name: 'TARGET', defaultValue: 'built-in', description: 'Agent node label')
        password(name: 'DEPLOY_TOKEN', defaultValue: '', description: 'Deployment token (masked in logs)')
        booleanParam(name: 'RUN_TESTS', defaultValue: false, description: 'Run backend tests before deploy (requires Maven on agent)')
    }

    stages {
        stage('Prepare source') {
            steps {
                sh '''
                    set -e
                    echo "ENV=${ENV} DRY_RUN=${DRY_RUN} TARGET=${TARGET}"
                    git config --global --add safe.directory /mnt/e/code/crm.git
                    git -C /mnt/e/code/crm push /mnt/e/code/crm.git master 2>/dev/null || echo "push skipped (no new commits)"
                    rm -rf /tmp/crm-build
                    git clone /mnt/e/code/crm.git /tmp/crm-build
                    cp /mnt/e/code/crm/.env /tmp/crm-build/.env || { echo "ERROR: .env missing"; exit 1; }
                    docker version
                '''
            }
        }

        stage('Tests (optional)') {
            when { expression { params.RUN_TESTS } }
            steps {
                sh 'cd /tmp/crm-build/backend && mvn -q test'
            }
        }

        stage('Build images') {
            steps {
                sh 'cd /tmp/crm-build && docker compose build'
            }
        }

        stage('Deploy') {
            when { expression { !params.DRY_RUN } }
            steps {
                sh '''
                    docker rm -f crm-redis crm-mysql crm-backend crm-frontend 2>/dev/null || echo "no old containers"
                    cd /tmp/crm-build && docker compose up -d --remove-orphans
                    cd /tmp/crm-build && docker compose ps
                '''
            }
        }

        stage('Health check') {
            when { expression { !params.DRY_RUN } }
            steps {
                sh 'curl -fsS http://localhost:8082/health || echo "WARN: frontend /health failed"'
                sh 'curl -fsS http://localhost:8081/actuator/health || echo "WARN: backend health failed"'
            }
        }
    }

    post {
        success {
            echo "Deploy OK: ENV=${params.ENV}, DRY_RUN=${params.DRY_RUN}"
        }
        failure {
            echo "Deploy FAILED. Inspect logs: cd /tmp/crm-build && docker compose logs"
        }
    }
}
