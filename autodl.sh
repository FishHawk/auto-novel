# 编译
rm -rf ./llama.cpp
git clone -q -c advice.detachedHead=false -b b3853 --depth 1 https://github.com/ggerganov/llama.cpp.git
cd llama.cpp
make GGML_CUDA=1 llama-server -j

# 运行
MODEL=sakura-14b-qwen2beta-v0.9-iq4_xs_ver2
# MODEL=sakura-32b-qwen2beta-v0.9-iq4xs
llama.cpp/server -m ${MODEL}.gguf -c 4096 -ngl 999 -a ${MODEL} --port 6006