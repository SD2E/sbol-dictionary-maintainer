FROM sd2e/python3 as basebuilder
RUN mkdir -p /app/src
WORKDIR /app
COPY ./setup.py .
# RUN python3 setup.py develop
RUN pip3 install --upgrade google-api-python-client google-auth-httplib2 google-auth-oauthlib
COPY ./src .
