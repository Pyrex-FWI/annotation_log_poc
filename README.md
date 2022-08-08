Symfony Demo Application
========================

docker build -t 3snetregistry.azurecr.io/tsme/php7.3:annotation_log .

docker push 3snetregistry.azurecr.io/tsme/php7.3:annotation_log

docker run -p 8002:8002 3snetregistry.azurecr.io/tsme/php7.3:annotation_log

http://127.0.0.1:8002/fr/public-api/move-in/create-reference


