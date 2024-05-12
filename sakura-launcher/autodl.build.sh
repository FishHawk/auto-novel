rm -rf ./llama.cpp

echo 准备编译llama.cpp...
git clone -q -c advice.detachedHead=false -b b2859 --depth 1 https://github.com/ggerganov/llama.cpp.git

echo 开始编译llama.cpp...
cd llama.cpp
make LLAMA_CUDA=1 server -j$(nproc) -s
ls -lh ./server

echo 编译完成