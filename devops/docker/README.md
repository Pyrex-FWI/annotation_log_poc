Tsme dev with docker
==================================
 

![archi](../../app/Resources/doc/docker-compose.png)

## Services overview

| Service                                                           | Domain
|-------------------------------------------------------------------| ---
| [Adminer](https://hub.docker.com/_/adminer)                       | Phpmyadmin like  
| [Apache](https://hub.docker.com/_/httpd)                          | Web server
| [Api simulator](https://hub.docker.com/r/apimastery/apisimulator) | Api rest mock tool
| [Blackfire](https://hub.docker.com/r/blackfire/blackfire)         | Performance testing, management and profiling
| ~~Haproxy~~ <!--[Haproxy](https://hub.docker.com/_/haproxy) -->   | ~~Reverse proxy  for load balancing~~
| [Maildev](https://hub.docker.com/r/maildev/maildev)               | Dev mailer 
| [Memcache](https://hub.docker.com/_/memcached)                    | Session and webservice cache
| [Mysql](https://hub.docker.com/_/mysql)                           | App databases storage
| ~~Node~~ <!--[Node](https://hub.docker.com/_/node) -->            | ~~frontend assets tool~~ 
| [OpenLdap](https://hub.docker.com/r/osixia/openldap)              | Users directory for intranet access 
| [Php/php-fpm](https://hub.docker.com/_/php)                       | Language interpreter
| [Phpldapadmin](https://hub.docker.com/r/osixia/phpldapadmin)      | Phpmyadmin for LDAP  
| [Traefik](https://hub.docker.com/_/traefik)                       | Edge router for docker dns resolution 
| [Varnish](https://hub.docker.com/_/varnish)                       | Reverse proxy for cache
| [Solr](https://hub.docker.com/_/solr)                          | Search engine

## How to run (Dev installation)

### Docker
  * Docker engine v1.19.0 or higher (`docker --version`). Your OS provided package might be a little old, if you encounter problems, do upgrade. See [https://docs.docker.com/engine/installation](https://docs.docker.com/engine/installation)
  * Docker compose [v1.29.0](https://github.com/docker/compose/releases/tag/1.29.2) =< version < 2 (`docker-compose --version`). See [docs.docker.com/compose/install](https://docs.docker.com/compose/install/)

### Assets (optional)
 * **Ask** a ezplatform medias archive
 * install it under `~/storage`

### Github access
  * Create a [Github access token](https://github.com/settings/tokens/new?description=tsme-access-token&scopes=repo,read:packages) 
  * Test [docker login on github registry](https://help.github.com/en/packages/using-github-packages-with-your-projects-ecosystem/configuring-docker-for-use-with-github-packages#authenticating-with-a-personal-access-token)
  * Create `.env.local` file with content
```
#https://github.com/settings/tokens
COMPOSER_AUTH={"github-oauth":{"github.com":"REPLACE WITH YOUR ACCESS TOKEN"}}
NPM_TOKEN=REPLACE_WITH_YOUR_ACCESS_TOKEN

COMPOSE_FILE=./docker-compose.yml:./../docker-compose-shared-network.yml
```

### Login on private docker registry

 Retrieve password from [OneNote - Environnements/Docker registry](https://one4u.sharepoint.com/sites/TSME/_layouts/15/Doc.aspx?sourcedoc={5e0464b1-5308-49e3-ad86-064789f4f90c}&action=edit&wd=target%28Environnements.one%7C1c5f26d0-eba5-4bb3-bbc4-bdf0f1699f34%2FDocker%20Registry%7C7a6f6ddc-9434-4afa-8e5d-092a4509a242%2F%29)
 
```bash
    $ docker login -u 3snetregistry 3snetregistry.azurecr.io
```

### Update your /etc/hosts
```bash
sudo -- sh -c -e "echo '127.0.0.1  suez.toutsurmoneau.test ebdm.toutsurmoneau.test intranet.toutsurmoneau.test riuc-admin.toutsurmoneau.test api.toutsurmoneau.test adminer.toutsurmoneau.test apisimulator.toutsurmoneau.test' >> /etc/hosts"
```

### Installation

```bash
# fix permissions issues (if you skip this the database services will fail)
make fix-perms
# start containers (it can take long time)
make up
# install vendor and compile assets
make composer-install

# enter into container
make bash
# once in container, install grumphp git hooks
php bin/grumphp git:init
# exit from container
exit

# move mysql ezplatform dev dump to database_in.sql.zip
# import dump into ez database
make mysql-import-ez-data

# load tsme_front database
make reload-database

# reload containers
make reload
```

Open `https://suez.toutsurmoneau.test` in your browser.

#### Know issues

- After installation, frontend go to error: `Impossible to create the root directory "/usr/var/www/app/../web/var/ezwebin_site/storage". mkdir(): Permission denied`

Update your "storage" folder permissions:
```shell
sudo chmod -R 777 ~/storage
```

- On Ubuntu, if you encounter an error when you try to start Traefik with "make up" 
```shell
Starting apps_edge-router_1 ... error
ERROR: for apps_edge-router_1  Cannot start service edge-router: driver failed programming external connectivity on endpoint apps_edge-router_1 (1036c8947b29bf5d2b3e699f49ee9f87c1377ec90ee7a936fbf0abe322ac5e81): Error starting userland proxy: listen tcp4 0.0.0.0:80: bind: address already in use
```
Just stop your local Apache `sudo service apache2 stop` and retry "make up"

To disable Apache from start up: `sudo update-rc.d apache2 disable`

### HTTPS

- [Install local authority](https://github.com/Suezenv/tsmx/blob/master/Devops/certificate/README.md)


### Blackfire

[Configure Blackfire](../../app/Resources/doc/blackFire.md)
