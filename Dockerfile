FROM 3snetregistry.azurecr.io/tsme/php7.3:xdebug-v4

COPY . /usr/var/www/

COPY ./devops/docker/php-fpm/php.ini /usr/local/etc/php/conf.d/99-php.ini

ENV APP_ENV=dev
ENV DATABASE_URL=sqlite:///%kernel.project_dir%/var/data/blog.sqlite
ENV MAILER_URL=null://localhost
ENV APP_SECRET=67d829bf61dc5f87a73fd814e2c9f629
ENV APP_DEBUG=1

ENTRYPOINT ["php", "-S", "0.0.0.0:8002", "-t", "public"]
