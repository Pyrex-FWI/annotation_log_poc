
#Generate private key

```
#Generate private key for CA
openssl genrsa 2048 > ca-key.pem

#Générate CA certificate
openssl req -new -x509 -nodes -days 3600 \
        -key ca-key.pem -out ca.pem -config ca.cnf
        
        
openssl req \
	-newkey rsa:2048 -nodes \
		-keyout server-key.pem \
		-subj "/C=FR/ST=IDF/L=NANTERRELAZONE/O=SmileServ/OU=Tsmx/CN=*.db" \
		-addext "subjectAltName = DNS:sql-master-1, DNS:localhost, DNS:127.0.0.1, DNS:sql-master-2" \
		-out server-req.pem 
openssl rsa -in server-key.pem -out server-key.pem
openssl x509 -req -in server-req.pem -days 3600 \
        -CA ca.pem -CAkey ca-key.pem -set_serial 01 -out server-cert.pem


openssl req \
	-newkey rsa:2048 -nodes \
		-keyout client-key.pem \
		-subj "/C=FR/ST=IDF/L=NANTERRELAZONE/O=SmileClient/OU=Tsmx/CN=*.db" \
		-out client-req.pem 
openssl rsa -in client-key.pem -out client-key.pem

openssl x509 -req -in client-req.pem -days 3600 \
        -CA ca.pem -CAkey ca-key.pem -set_serial 01 -out client-cert.pem
        
openssl verify -CAfile ca.pem server-cert.pem client-cert.pem
```
