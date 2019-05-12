FROM sd2e/java8:ubuntu16
MAINTAINER Erik Ferlanti <eferlanti@tacc.utexas.edu>

RUN apt-get update \
    && apt-get upgrade -y \
    && apt-get install -y wget vim.tiny \
    && rm -rf /var/lib/apt/lists/*

RUN mkdir /app
WORKDIR /app

COPY build/libs/*.jar /app/
COPY tokens /app/tokens
COPY run_sd2_dictionary_maintainer.sh /app/

ENV PATH "${PATH}:/app"

CMD ["run_sd2_dictionary_maintainer.sh"]
