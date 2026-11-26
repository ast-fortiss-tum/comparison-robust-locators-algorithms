FROM abk-php:5.6
# PHP-Fusion 7.02 needs GD (avatar/photo processing). Debian stretch is archived (expired repository keys).
RUN sed -i 's|deb.debian.org|archive.debian.org|g; s|security.debian.org/debian-security|archive.debian.org/debian-security|g; /stretch-updates/d' /etc/apt/sources.list \
 && echo 'Acquire::Check-Valid-Until "false"; Acquire::AllowInsecureRepositories "true"; APT::Get::AllowUnauthenticated "true";' > /etc/apt/apt.conf.d/99archive \
 && apt-get update -qq && apt-get install -y -qq --allow-unauthenticated libpng-dev libjpeg-dev libfreetype6-dev >/dev/null \
 && docker-php-ext-configure gd --with-freetype-dir=/usr --with-jpeg-dir=/usr --with-png-dir=/usr >/dev/null && docker-php-ext-install -j2 gd >/dev/null && php -m | grep -i gd
