#!/bin/bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"

export PATH="/usr/local/bin/aws":$PATH
export AWS_DEFAULT_REGION="eu-west-3"
export AWS_ACCOUNT_ID="654654249479"
export AWS_ACCESS_KEY_ID="AKIAZQ3DPDIDQTSJSDOQ"
export AWS_SECRET_ACCESS_KEY="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"
export AWS_EC2_SSH_KEYPAR_PATH="mykeypair.pem"
export AWS_KEYPAIR_NAME="mykeypair"
