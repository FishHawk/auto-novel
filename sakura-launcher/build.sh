VERSION=$1
if [ $# -ne 1 ]
then
    return
fi

mkdir -p build/$VERSION
cd build

declare -a arr=(
    "cudart-llama-bin-win-cu11.7.1-x64.zip"
    "llama-$VERSION-bin-win-cublas-cu11.7.1-x64.zip"
    #
    "cudart-llama-bin-win-cu12.2.0-x64.zip"
    "llama-$VERSION-bin-win-cublas-cu12.2.0-x64.zip"
    #
    "llama-$VERSION-bin-win-avx-x64.zip"
    "llama-$VERSION-bin-win-avx2-x64.zip"
    "llama-$VERSION-bin-win-avx512-x64.zip"
)

for i in "${arr[@]}"
do
    wget -P $VERSION -nc https://github.com/ggerganov/llama.cpp/releases/download/$VERSION/$i
done

pack () {
    mkdir -p $1/llama
    unzip -n $VERSION/${arr[$2]} -d $1/llama
    if [ $# -eq 3 ]
    then
        unzip -n $VERSION/${arr[$3]} -d $1/llama
    fi
    cp ../src/* $1/
    zip -r $1.zip $1
}

pack "sakura-launcher-cublas11-$VERSION" 0 1
pack "sakura-launcher-cublas12-$VERSION" 2 3
pack "sakura-launcher-avx-$VERSION" 4
pack "sakura-launcher-avx2-$VERSION" 5
pack "sakura-launcher-avx512-$VERSION" 6