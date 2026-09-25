// CRM 部署流水线（Jenkins）
// 部署方式：经 WSL Ubuntu-22.04 执行 docker compose（docker 引擎运行在 WSL 内）。
// 代码不经过 SCM checkout —— 仓库无 remote，直接操作本机工作目录。
//   Windows: E:\code\crm   <==>   WSL: /mnt/e/code/crm

pipeline {
    agent { label "${params.TARGET ?: 'built-in'}" }

    options {
        timestamps()
        disableConcurrentBuilds()
    }

    parameters {
        choice(name: 'ENV', choices: ['dev', 'staging', 'prod'], description: 'Deployment environment (maps to .env)')
        booleanParam(name: 'DRY_RUN', defaultValue: true, description: 'true = build images only, do NOT run `up -d`')
        string(name: 'TARGET', defaultValue: 'built-in', description: 'Agent node label')
        password(name: 'DEPLOY_TOKEN', defaultValue: '', description: 'Deployment token (masked in logs)')
        booleanParam(name: 'RUN_TESTS', defaultValue: false, description: 'Run backend tests before deploy (slow, off by default)')
    }

    environment {
        WSL_DISTRO = 'Ubuntu-22.04'
        WSL_DIR    = '/mnt/e/code/crm'
        WIN_DIR    = 'E:\\code\\crm'
    }

    stages {
        stage('Preflight') {
            steps {
                echo "ENV=${params.ENV}, DRY_RUN=${params.DRY_RUN}, TARGET=${params.TARGET}"
                bat "if not exist \"${WIN_DIR}\\docker-compose.yml\" (echo [ERROR] docker-compose.yml missing & exit /b 1)"
                bat "if not exist \"${WIN_DIR}\\.env\" (echo [ERROR] .env missing — copy .env.example first & exit /b 1)"
                bat "wsl -d ${WSL_DISTRO} -- docker version"
            }
        }

        stage('Tests (optional)') {
            when { expression { params.RUN_TESTS } }
            steps {
                bat "cd /d ${WIN_DIR}\\backend && mvn -q test"
            }
        }

        stage('Build images') {
            steps {
                bat "wsl -d ${WSL_DISTRO} -- bash -lc \"cd ${WSL_DIR} && docker compose build\""
            }
        }

        stage('Deploy') {
            when { expression { !params.DRY_RUN } }
            steps {
                bat "wsl -d ${WSL_DISTRO} -- bash -lc \"cd ${WSL_DIR} && docker compose up -d --remove-orphans\""
                bat "wsl -d ${WSL_DISTRO} -- bash -lc \"cd ${WSL_DIR} && docker compose ps\""
            }
        }

        stage('Health check') {
            steps {
                bat 'curl -fsS http://localhost/health || exit /b 1'
                bat 'curl -fsS http://localhost:8081/actuator/health || exit /b 1'
            }
        }
    }

    post {
        success {
            echo "Deploy OK: ENV=${params.ENV}, DRY_RUN=${params.DRY_RUN}"
        }
        failure {
            echo "Deploy FAILED. Inspect logs: wsl -d ${WSL_DISTRO} -- bash -lc 'cd ${WSL_DIR} && docker compose logs'"
        }
    }
}
