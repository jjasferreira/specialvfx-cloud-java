# cnv24-g33

## Cloud Computing and Virtualization 2023-24 Project

Group 33:

- João Vieira - 99250
- José João Ferreira - 99259
- Rui Moniz - 99323

### aslb

This directory stores all the necessary files to setup and deploy our AutoScaler & LoadBalancer server on the AWS cloud.

### aws-scripts

This directory contains all the scripts used to deploy and manage our AWS instances.
You should follow the instructions in the [README.md](aws-scripts/README.md) file of this folder to properly compile all code and deploy the servers in the correct order.

### specialvfx

This directory contains all SpecialVFX@Cloud code modules.

---

### Test system with web interface:

http://grupos.ist.utl.pt/~meic-cnv.daemon/project/

---

### Test system without web interface:

#### Image Processing (Blur and Enhance):

##### Encode in Base64.

base64 picture.jpg > temp.txt

##### Append a formatting string.

echo -e "data:image/jpg;base64,$(cat temp.txt)" > temp.txt

##### Send the request.

curl -X POST http://127.0.0.1:8000/blurimage --data @"./temp.txt" > result.txt
curl -X POST http://127.0.0.1:8000/enhanceimage --data @"./temp.txt" > result.txt
(replace 127.0.0.1 with the public IP of the ASLB server)

##### Remove a formatting string (remove everything before the comma).

sed -i 's/^[^,]\*,//' result.txt

##### Decode from Base64.

base64 -d result.txt > result.jpg

#### Ray Tracing:

##### Add scene.txt raw content to JSON.

cat scene.txt | jq -sR '{scene: .}' > payload.json

##### Add texmap.bmp binary to JSON (optional step, required only for some scenes).

hexdump -ve '1/1 "%u\n"' texmap.bmp | jq -s --argjson original "$(<payload.json)" '$original \* {texmap: .}' > payload.json

##### Send the request.

curl -X POST http://127.0.0.1:8000/raytracer?scols=400\&srows=300\&wcols=400\&wrows=300\&coff=0\&roff=0\&aa=false --data @"./payload.json" > result.txt
(replace 127.0.0.1 with the public IP of the ASLB server)

##### Remove a formatting string (remove everything before the comma).

sed -i 's/^[^,]\*,//' result.txt

##### Decode from Base64.

base64 -d result.txt > result.bmp
