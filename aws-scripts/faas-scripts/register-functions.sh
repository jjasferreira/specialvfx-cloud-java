#!/bin/bash

source ../config.sh

# Create Raytracer function
aws lambda create-function \
	--function-name raytrace-func \
	--zip-file fileb://../../webserver/raytracer/target/raytracer-1.0.0-SNAPSHOT-jar-with-dependencies.jar \
	--handler pt.ulisboa.tecnico.cnv.raytracer.RaytracerHandler \
	--runtime java11 \
	--timeout 5 \
	--memory-size 256 \
	--role arn:aws:iam::$AWS_ACCOUNT_ID:role/ASLBRole

# Create Blur function
aws lambda create-function \
        --function-name blur-func \
	--zip-file fileb://../../webserver/imageproc/target/imageproc-1.0.0-SNAPSHOT-jar-with-dependencies.jar \
        --handler pt.ulisboa.tecnico.cnv.imageproc.BlurImageHandler \
        --runtime java11 \
	--timeout 5 \
	--memory-size 256 \
        --role arn:aws:iam::$AWS_ACCOUNT_ID:role/ASLBRole

# Create Enhance function
aws lambda create-function \
        --function-name enhance-func \
	--zip-file fileb://../../webserver/imageproc/target/imageproc-1.0.0-SNAPSHOT-jar-with-dependencies.jar \
        --handler pt.ulisboa.tecnico.cnv.imageproc.EnhanceImageHandler \
        --runtime java11 \
	--timeout 5 \
	--memory-size 256 \
        --role arn:aws:iam::$AWS_ACCOUNT_ID:role/ASLBRole

echo "All functions have been deployed"
