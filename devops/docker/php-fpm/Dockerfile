FROM php:7.3-fpm-buster as base
WORKDIR "/usr/var/www"

# Fix debconf warnings upon build
ARG DEBIAN_FRONTEND=noninteractive

# Install selected extensions and other stuff
RUN ln -s /usr/local/bin/php /usr/bin/php73 && \
    apt-get update  && apt upgrade -y && \
    apt-get -y --no-install-recommends install \
    git procps wget telnet curl apt-transport-https imagemagick unzip locales graphviz libldap2-dev rsync \
    # Human Language and Character Encoding Support:
    zlib1g-dev libicu-dev g++ libzip-dev && \
    docker-php-ext-configure intl && \
    docker-php-ext-install intl && \
    # SOAP
    apt-get install -y --no-install-recommends libxml2-dev && \
    docker-php-ext-configure soap && \
    docker-php-ext-install soap && \
    # Zip
    docker-php-ext-configure zip && \
    docker-php-ext-install zip && \
    # Mysql
    docker-php-ext-configure pdo_mysql && \
    docker-php-ext-install pdo_mysql && \
    # AMQP
    apt install -y librabbitmq-dev && \
    pecl install amqp && \
    docker-php-ext-enable amqp && \
    # ssh2 \
    apt-get install -y libssh2-1-dev libssh2-1 && \
    pecl install ssh2-1.3.1 && \
    docker-php-ext-enable ssh2 && \
    # bcmath
    docker-php-ext-configure bcmath && \
    docker-php-ext-install bcmath && \
    # sockets
    docker-php-ext-configure sockets && \
    docker-php-ext-install sockets &&\
    #xsl
    apt-get install --no-install-recommends -y libxslt1-dev && \
    docker-php-ext-configure xsl &&  docker-php-ext-install xsl && \
    #memcached
    apt-get install -y --no-install-recommends \
    libmemcached-dev && \
    pecl install memcached-3.1.5 \
    && docker-php-ext-enable memcached && \
    apt-get install -y --no-install-recommends libpng-dev libfreetype6-dev libjpeg-dev zlib1g-dev && \
    docker-php-ext-configure gd --with-freetype-dir=/usr/include/ --with-jpeg-dir=/usr/include/ && \
    docker-php-ext-install gd && \
    docker-php-ext-install opcache && \
    docker-php-ext-install ldap && \
    docker-php-ext-install pcntl && \
    docker-php-ext-install shmop && \
    docker-php-ext-install calendar && \
    docker-php-ext-configure calendar && \
    echo "fr_FR.UTF-8 UTF-8" > /etc/locale.gen && locale-gen fr_FR.UTF-8 && \
###########################################################################
# INSTALL JAVA for assetic:dump
###########################################################################
    mkdir -p /usr/share/man/man1 && apt-get install -y --no-install-recommends openjdk-11-jre-headless && rm -rf /var/lib/apt/lists/*

ENV LANG fr_FR.UTF-8

COPY --from=composer:2 /usr/bin/composer /usr/bin/composer

ARG INCLUDE_NODE=false
RUN if [ ${INCLUDE_NODE} = true ]; then \
    curl -sL https://deb.nodesource.com/setup_12.x -o nodesource_setup.sh \
    && bash ./nodesource_setup.sh \
    && apt-get install --no-install-recommends nodejs -y \
    && rm nodesource_setup.sh \
    && curl -sS https://dl.yarnpkg.com/debian/pubkey.gpg | apt-key add - \
    && echo "deb https://dl.yarnpkg.com/debian/ stable main" | tee /etc/apt/sources.list.d/yarn.list \
    && apt-get update &&  apt-get install --no-install-recommends yarn -y  && rm -rf /var/lib/apt/lists/* \
;fi

RUN curl https://github.com/fabpot/local-php-security-checker/releases/download/v1.0.0/local-php-security-checker_1.0.0_linux_amd64 -sL -o /bin/local-php-security-checker \
        && chmod +x /bin/local-php-security-checker

ADD php-fpm/php.ini /usr/local/etc/php/conf.d/99-php.ini

RUN version=$(php -r "echo PHP_MAJOR_VERSION.PHP_MINOR_VERSION;") \
        && curl -A "Docker" -o /tmp/blackfire-probe.tar.gz -D - -L -s https://blackfire.io/api/v1/releases/probe/php/linux/amd64/$version \
        && mkdir -p /tmp/blackfire \
        && tar zxpf /tmp/blackfire-probe.tar.gz -C /tmp/blackfire \
        && mv /tmp/blackfire/blackfire-*.so $(php -r "echo ini_get ('extension_dir');")/blackfire.so \
        && printf "extension=blackfire.so\nblackfire.agent_socket=tcp://blackfire:8707\n" > $PHP_INI_DIR/conf.d/blackfire.ini \
        && mkdir -p /tmp/blackfire \
               && curl -A "Docker" -L https://blackfire.io/api/v1/releases/client/linux_static/amd64 | tar zxp -C /tmp/blackfire \
               && mv /tmp/blackfire/blackfire /usr/bin/blackfire \
               && rm -Rf /tmp/blackfire \
        && rm -rf /tmp/blackfire /tmp/blackfire-probe.tar.gz \
        && rm -rf /var/lib/apt/lists/*

RUN curl https://github.com/fabpot/local-php-security-checker/releases/download/v1.0.0/local-php-security-checker_1.0.0_linux_amd64 -sL -o /bin/local-php-security-checker \
        && chmod +x /bin/local-php-security-checker

RUN echo "export LS_OPTIONS='--color=auto'" >> ~/.bashrc \
    && echo "alias ls='ls $LS_OPTIONS'" >> ~/.bashrc \
    && echo "alias ll='ls $LS_OPTIONS -l'" >> ~/.bashrc \
    && echo "alias l='ls $LS_OPTIONS -lA'" >> ~/.bashrc \
    && echo "alias l='ls $LS_OPTIONS -lA'" >> ~/.bashrc \
    && echo "alias phpbench='/root/.composer/vendor/bin/phpbench'" >> ~/.bashrc

#Install clamAV
RUN apt update && apt install clamav clamav-daemon -y && rm -rf /var/lib/apt/lists/* \
    && echo "TCPSocket 3310" >> /etc/clamav/clamd.conf \
    && echo "TCPAddr 127.0.0.1" >> /etc/clamav/clamd.conf \
    && sed -i 's/User clamav/User root/g' /etc/clamav/clamd.conf \
    && freshclam \
    && curl -k -o /usr/lib/eicar.com.txt https://www.eicar.org/download/eicar.com.txt && echo "simpleFile" > /usr/lib/clean.txt

COPY .mitmproxy/mitmproxy-ca-cert.pem /usr/share/ca-certificates/
RUN chmod 644 /usr/share/ca-certificates/mitmproxy-ca-cert.pem && update-ca-certificates --fresh

ARG NEW_RELIC_AGENT_VERSION

RUN curl -vL https://download.newrelic.com/php_agent/archive/${NEW_RELIC_AGENT_VERSION}/newrelic-php5-${NEW_RELIC_AGENT_VERSION}-linux.tar.gz | tar -C /tmp -zx \
    && export NR_INSTALL_USE_CP_NOT_LN=1 \
    && export NR_INSTALL_SILENT=1 \
    && /tmp/newrelic-php5-${NEW_RELIC_AGENT_VERSION}-linux/newrelic-install install \
    && rm -rf /tmp/newrelic-php5-* /tmp/nrinstall*


# Xdebug
FROM base as xdebug
RUN pecl install xdebug-2.9.8  && docker-php-ext-enable xdebug

#RUN apt-get clean; rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/* /usr/share/doc/*
