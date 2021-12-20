#!/usr/bin/env groovy
// Liste des libraries à importer
@Library(['pipeline-library-stm', 'y80-microservice-pipelines']) _
import fr.donnees.bazinga.VaultHelper

pipeline {

    agent {
        label {
            label ''
            customWorkspace("workspace/${env.JOB_NAME}")
        }
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '5', artifactNumToKeepStr: '5'))
        ansiColor("xterm")
        timestamps()
        timeout(time: 15, unit: "MINUTES")
    }

    parameters {
        string(
            name: 'CICD_MODE', 
            defaultValue: 'BUILD,DEPLOY', 
            description: '<br/><div><strong>Choose what the job should do</strong><br/><span style="color: blue">BUILD</span>   : builds the artifacts and the docker image, push artifacts and docker image<br/><span style="color: blue">DEPLOY</span>  : deploys on openshift a specific version (VERSION **must** be specified)<br/><span style="color: blue">BUILD & DEPLOY</span> : builds the artifacts and the docker image, push artifacts and docker image, and finaly deploys on openshift<br/><br/></div>')
        choice(
            name: 'SERVICE', 
            choices: ["-- choix produit --","orchestrator","enginer-model-lmm"], 
            description: '<div><strong>Micro service à déployer</strong><br/><br/></div>')
        string(
            name: 'GIT_BRANCH', 
            defaultValue: '', 
            description: '<div><strong>Branche git à builder</strong><br/><br/></div>')
        string(
            name: 'VERSION', 
            defaultValue: '', 
            description: '<div><strong>Version "RELEASE" avec le format X.X.X (ex: 1.0.0)</strong><br/><br/></div>')
        choice(
            name: 'ENV_DEPLOY', 
            choices: ["-- Choix d'env --","dev","qlf","perf","val","pprd","prod"], 
            description: '<div><strong>Environnement du déploiement de l\'image</strong><br/><br/><br/></div>')
    }
    
    tools {
        jdk 'jdk8'
        maven 'M3.5.4'
    }

    environment {
        
        application = "${params.SERVICE}"
        versionApp = "${params.VERSION}"
        environnement = "${params.ENV_DEPLOY}"
        pullerSecretName = null
        propsK8S = null
    }

    stages {
        stage("Init") {
            when {
                expression {
                    def expression1 = (application != '' && !application.contains("--"))
                    def expression2 = (environnement != '' && !environnement.contains("--"))
                    def expression3 = (params.VERSION != '')
                    def expression4 = (params.GIT_BRANCH != '')
                    
                    return ((expression1 && expression2 && expression3) || (expression1 && expression4));
                }
            }
            steps {
                
                jobDisplayName("${env.BUILD_NUMBER} | ${application} | ${params.GIT_BRANCH} ${params.VERSION} | ${environnement}", "<h3>Build du microservice ${application}</h3>")
                gitClean()
                script {
                    
                    // Recuperation des proprietes
                    VaultHelper vault = new VaultHelper(this, 'Y80').login()
                    propsK8S = stmKubernetes.getProperties(environnement, "kubernetes/properties")
                    env.KUBERNETES_SERVICE_HOST = propsK8S['kubernetes_url']
                    env.PROJECT_NAME = propsK8S['namespace']
                    env.HTTP_DEBUG = "false"
                    
                    //Avoir la version à deployer & url vers l'image docker
                    if (params.CICD_MODE.equals('DEPLOY')) {
                    	versionApp = "${params.VERSION}"
                        env.DOCKER_IMG = "artifactory-principale.fr:9994/pml/${application}:${versionApp}"
                        pullerSecretName = "y80-image-puller-release"
                    } else {
                        def repoEnv = environnement;
                        if (params.CICD_MODE.equals('BUILD')) {
                            repoEnv = 'staging'
                            environnement = "dev"
                        }
                        //def pom = readMavenPom file:"pom.xml"
                    	versionApp = readMavenPom().getVersion()
                    	env.DOCKER_IMG = "artifactory-principale.fr:9993/pml/${repoEnv}/${application}:${versionApp}"
                        pullerSecretName = "y80-image-puller" //"y80-image-puller-staging"
                    }
                    env.KUBERNETES_TOKEN = stmKubernetes.findToken( vault, environnement.toString().toUpperCase(), "PML/kubernetes" )
                    
                    // Synchronisation des secrets (Kyss -> K8S)
                    y80ConfigsApplication.synchroSecretKyss(vault, environnement.toString().toUpperCase(), application)
                }
            }
        }
		stage("Build") {
            when {
                expression { return params.CICD_MODE.contains('BUILD') }
            }
            environment {
         		MVN_SETTINGS='y80-maven-settings'
    		}
            steps {
            
                script {
	                // Build java with maven + Push to artifactory
	                mvn 'deploy'
	                
                    def dockerBuildArgs = "--no-cache --build-arg POM_VERSION=${versionApp} --build-arg ARTIFACT_ID=${application} ."
                    // Registry de STAGES
                    docker.withRegistry( "http://artifactory-principale.fr:9993", 'Y80_ARTIFACTORY_RW' ) {
                        docker.build(env.DOCKER_IMG, dockerBuildArgs ).push()
                    }
                }
            }
        }
        stage("Deploy") {
            when {
                expression { return params.CICD_MODE.contains('DEPLOY') }
            }
            steps {
                script {
                    
                    //Ajout des properties pour kubernetes
                    propsK8S['deploy.env'] = "${environnement}"
                    propsK8S['deploy.image'] = "${environnement}"
                    propsK8S['artifact.id'] = "${versionApp}"
                    propsK8S['profile'] = "${environnement}"
                    propsK8S['version'] = "${versionApp}"
                    propsK8S['imagedocker'] = "${env.DOCKER_IMG}"
                    propsK8S['imagepuller'] = "${pullerSecretName}"
                    
                    def resourceURL = propsK8S['kubernetes_url']+"/apis/apps/v1/namespaces/"+propsK8S['namespace']+"/deployments/pml-${application}-${environnement}";
                    
                    // Deploiement des ressources
                    stmKubernetes.deployResources(propsK8S,
                        stmKubernetes.resource('Deployment', "pml-${application}-${environnement}", "kubernetes/configs/deployment.yml" ),
                        stmKubernetes.resource('Service', "pml-${application}-${environnement}", "kubernetes/configs/service.yml" ),                                               
                        stmKubernetes.resource('IngressRoute', "pml-${application}-${environnement}", "kubernetes/configs/route.yml" )
                    )
                    
                    // Rollout si la resource exist
                    if (y80DeployResource.getStatusUnavailableReplicas(resourceURL)) {
                       stmKubernetes.rollout(resourceURL, [build: "${env.BUILD_NUMBER}"]); 
                    }
                    
                    // On attend que la ressource soit prete
                    def isReady = y80DeployResource.waitForDeployment(resourceURL, 90)
                    if (!isReady) throw new Exception("Le pod mets trop de temps pour démarrer");
                }
            }
        }
        stage("Promote image") {
            when {
                expression { return params.CICD_MODE.contains('DEPLOY') }
            }
            steps {
                script {
                    //créer le tag de l'image une fois que toutes les étapes précédentes se sont déroulées sans erreur + suppression de l'image en local
                    sh returnStdout: true, script: "docker tag ${env.DOCKER_IMG} ${env.DOCKER_IMG}\n docker rmi $env.DOCKER_IMG"
                } 
            }
        }
    }

    post {
        always {
            deleteDir()
        }
    }
}
