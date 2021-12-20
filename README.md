




# orchestrator

spring boot component for scheduling SQL Teradata and / or http processes. 
-Orchestrator is responsible for the batch mode scheduling logic. 
-Orchestrator provides for its own data model to declare the software components that are a member of the batch 
-Each member software component is sufficiently unitary to leave the freedom of scheduling and scalability to Orchestrtator, for example for Teradata a macro with single query  is the most appropriate .
-A matrix of functional dependencies between the members of the same batch to be carried out to declare the batch on Orchestrator
-Orchestrator was designed declaratively and can scale declaratively
-Orchestrator use the IMDG (In-Memory Data Grids) hazelcast library to save the execution state in memory in order to facilitate scalability by becoming stateless
-The hazelcast cluster is elastic, it automatically detects new nodes in autodiscovery after a scale out operation






############################################################
composant spring boot d'ordonnancement des traitements Teradata et/ou http.
-Orchestrator est résponsable de la logique d'ordonnancement en mode batch.
-Orchestrator prévoie un model de données propre à lui pour déclarer les composants logiciels membre du batch
-Chaque composant logiciel membre est suffisament unitaire pour laisser la liberté d'ordonnancement et de scalabilité à Orchestrtator par exemple pour Teradata une macro mono requête est la plus adéquate.
-Une matrice de dépendances fonctionnelles entre les membres du même batch à réaliser pour déclarer le batch sur Orchestrator
-Orchestrator a été conçue de manière déclarative et peut monter en charge déclarativement
-Orchestrator utilise la librairie IMDG (In-Memory Data Grids)  hazelcast pour sauvegarder l'état d'éxecution en mémoire afin de faciliter la scalabilité en devenant stateless
--Le cluster hazelcast est élastique il detecte automatiquement les nouveaux nodes en autodiscovery après une opération de scale out


