DIR=$(realpath "$(dirname "${BASH_SOURCE[0]}")")
python3 -m grpc_tools.protoc -I$DIR/../../../proto --python_out=$DIR/../src/grpc_client_server/grpc_generated --pyi_out=$DIR/../src/grpc_client_server/grpc_generated --grpc_python_out=$DIR/../src/grpc_client_server/grpc_generated $DIR/../../../proto/mqtt_client_control.proto
