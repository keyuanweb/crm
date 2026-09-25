// CRM 部署流水线（Jenkins）
// 运行环境：Built-In Node = Linux (amd64)，Jenkins 与 docker 引擎同在 WSL Ubuntu-22.04。
// 部署方式：直接 docker compose（无需 wsl 前缀、无需 SCM checkout）。
// 代码路径：/mnt/e/code/crm（Windows E:\code\crm 的 WSL 挂载点）。

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
        stage('Preflight') {
            steps {
                sh '''
                    set -e
                    echo "ENV=${ENV} DRY_RUN=${DRY_RUN} TARGET=${TARGET}"
                    test -f /mnt/e/code/crm/docker-compose.yml || { echo "ERROR: docker-compose.yml missing"; exit 1; }
                    test -f /mnt/e/code/crm/.env || { echo "ERROR: .env missing (cp .env.example .env first)"; exit 1; }
                    docker version
                '''
            }
        }

        stage('Tests (optional)') {
            when { expression { params.RUN_TESTS } }
            steps {
                sh 'cd /mnt/e/code/crm/backend && mvn -q test'
            }
        }

        stage('Build images') {
            steps {
                sh 'cd /mnt/e/code/crm && docker compose build'
            }
        }

        stage('Deploy') {
            when { expression { !params.DRY_RUN } }
            steps {
                sh 'cd /mnt/e/code/crm && docker compose up -d --remove-orphans'
                sh 'cd /mnt/e/code/crm && docker compose ps'
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
            echo "Deploy FAILED. Inspect logs: cd /mnt/e/code/crm && docker compose logs"
        }
    }
}
