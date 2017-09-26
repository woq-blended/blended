#!/bin/sh

DAYS=365

echo ">>> Creating server certificate for ${DAYS} days. Today is $(date)"

# Generate a private key
echo ">>> You will be asked for a password. You can choose a simple one, we will remove it again in a second."
openssl genrsa -des3 -out server.key 4096
# Generate a CSR (Certificate Signing Request)
#openssl req -new -key server.key -out server.csr
openssl req -new -key server.key -out server.csr
# copy server key
cp server.key server.key.org
# remove pass prase from server key
echo
echo ">>> Now we will remove the password from the server key."
openssl rsa -in server.key.org -out server.key
# generate a self-signed certificate
openssl x509 -req -days ${DAYS} -in server.csr -signkey server.key -out server.crt
# make server key only readable by root
#chmod 400 server.key
echo
echo ">>> Now, you can copy 'server.key' and 'server.crt' to the server, e.g. Apache."
echo ">>> You should chmod the server.key to 400."
echo
echo ">>> For Jetty, we create now a combined key and certificate PKCS12 file."
echo ">>> Please give a export passwort for the 'server.pkcs12' file."
openssl pkcs12 -inkey server.key -in server.crt -export -out server.pkcs12
echo
echo ">>> Now, we create a kestore for direct use in Jetty."
echo ">>> You will have to enter the export passwort from the last step as 'input passphrase'."
echo ">>> The 'output passphrase' must appear in your jetty.xml config as both the Password and the KeyPassword of the SunJsseListener."
keytool -importkeystore -srckeystore server.pkcs12 -srcstoretype PKCS12 -destkeystore keystore

unset DAYS
