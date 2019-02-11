FROM sd2e/python3 as basebuilder
RUN mkdir -p /app/src
WORKDIR /app
COPY ./setup.py .
RUN python3 setup.py develop
COPY ./src /app
