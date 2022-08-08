

#### Deployer un package dans le workspace d'intégration d'un développeur

```
ansible-playbook -i devops/ansible/inventory/inte \
 devops/ansible/users-workspace-deploy.yml \
 -e deploy_version=TSME-PART-build-70 \
 -e target_env=inte \
 -e for_user=chpyr -v
```

- **for_user** : Workspace de l'utilisateur à cibler.
- **deploy_version**: Le nom de package à déployer qui doit être à la racine du projet.
- **target_env**: environnement cible (ne doit pas être remplacé. Impacte sur la configuration à utilisé lors du déploiement)

- *devops/ansible/inventory/inte*, peut éventuellement être remplacé par *devops/ansible/inventory/smile-inte* lors d'une livraison via le vpn smile