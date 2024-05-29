#!/bin/bash

source ../config.sh

aws lambda delete-function --function-name raytrace-func
aws lambda delete-function --function-name blur-func
aws lambda delete-function --function-name enhance-func

echo "All functions have been de-registered"
