# Contributing Guideline



## 部署运行



### 一般 Linux 发行版

```bash
> git clone https://github.com/FishHawk/auto-novel.git
> cd auto-novel
```

创建并编辑 .env 文件：

```bash
# .env file 具体内容参见 docker-compose.yml 的 environment 设置项
DATA_PATH=./data   # 所有数据的存储位置

HTTPS_PROXY=https://xx.xx.xx.xx:xxxx  # https 代理，可以为空
MAILGUN_API_KEY=ANYTHING              # Mailgun 邮件服务，本地运行无需配置
HAMELN_TOKEN=ANYTHING                 # 本地运行无需配置
JWT_SECRET=ANYTHING                   # JWT secret，本地开发运行可以随便填
PIXIV_COOKIE_PHPSESSID=ANYTHING       # Pixiv cookies，不涉及到 pixiv 的情况下可以随便填

```

编辑 `docker-compose.yml` 文件，酌情修改参数
<details>
<summary>
`docker-compose.yml` 文件，点击展开。
</summary>

```yaml
# docker-compose.yml
services:
  proxy:
    image: ghcr.io/fishhawk/wneg-proxy
    ports:
      - 80:80
    volumes:
      - ${DATA_PATH}/files-temp:/data/files-temp
      - ${DATA_PATH}/files-wenku:/data/files-wenku
      - ${DATA_PATH}/files-extra:/data/files-extra
    restart: always   # 如果不想开机自启，请注释掉

  server:
## 二选一，使用预编译好的最新版或者本地 GitHub 编译的版本
## 非开发的话用预编译好的版本
    image: ghcr.io/fishhawk/wneg-server
#   build:
#     context: ./server/
#     dockerfile: Dockerfile
   depends_on:
     - mongo
   environment:
     - HTTPS_PROXY
     - MAILGUN_API_KEY
     - JWT_SECRET
     - HAMELN_TOKEN
     - PIXIV_COOKIE_PHPSESSID
     - DB_HOST_MONGO=mongo
     - DB_HOST_ES=elasticsearch
     - DB_HOST_REDIS=redis
   ports:
     - 8081:8081
   volumes:
     - ${DATA_PATH}/files-temp:/data/files-temp
     - ${DATA_PATH}/files-wenku:/data/files-wenku
     - ${DATA_PATH}/files-extra:/data/files-extra
   restart: always   # 如果不想开机自启，请注释掉

  mongo:
    image: mongo:6.0.3
    environment:
      - MONGO_INITDB_DATABASE=auth
    ports:
      - 27017:27017
    volumes:
      - ${DATA_PATH}/db:/data/db
    restart: always   # 如果不想开机自启，请注释掉

  # 注意 elasticsearch 启动后会非常非常占用内存，请确保有足够内存。
  elasticsearch:
    image: elasticsearch:8.6.1
    environment:
      - xpack.security.enabled=false
      - discovery.type=single-node
    ulimits:
      memlock:
        soft: -1
        hard: -1
      nofile:
        soft: 65536
        hard: 65536
    cap_add:
      - IPC_LOCK
    volumes:
      - ${DATA_PATH}/es/data:/usr/share/elasticsearch/data
      - ${DATA_PATH}/es/plugins:/usr/share/elasticsearch/plugins
    ports:
      - 9200:9200
    restart: always   # 如果不想开机自启，请注释掉

  redis:
    image: redis:7.2.1
    ports:
      - 6379:6379
    restart: always   # 如果不想开机自启，请注释掉
```
</details>



确认 `.env` 文件无误后，执行 `docker compose up [-d]`  (`-d` 为后台运行)

本地访问 `http://localhost` 即可。



### 混乱邪恶のNixOS

NixOS 这种邪教配置的 `commit` 被 `fishhawk` 拒绝了。
这里放一个自用的 [`flake.nix`](https://gist.github.com/kurikomoe/9dd60f9613e0b8f75c137779d223da4f)

注意由于用了 `devenv`，可能需要 `--impure` 启动。
```envrc
use flake . --impure
```



## 开发说明


> [!NOTE]
> 请选择你的身份：前端开发还是开发后端（还是全栈大佬）



### 前端 web

前端的配置比较简单，一般来说可以直接连接到主站进行测试
（已经做了过滤，开发模式下不会向上游提交翻译结果）

创建并编辑 `web` 目录下的 `.env` 文件：
```env
LOCAL=1   # 实际上 LOCAL 字段存在则为本地测试（连接后端 localhost:8081）
          # 注释掉 LOCAL=1 为连接到主站。
```

执行 `pnpm dev` 即可启动本地前端开发环境。

--------------------------------------------------------------------------------

### 后端 server

后端开发的话，建议修改 `docker-compose.yml`，
假设修改后的文件为 `docker-compose-dev.yml`。

> `docker-compose-dev.yml` 中注释掉 server，这样可以单独重启 server。
>
> 避免开发测试时需要频繁启动所有依赖项。

> [!NOTE]
> 必要的时候可以把 `proxy` 也关闭掉，手动启动前端，防止 docker 造成的一些网络问题。
>
> 同时考虑到后端手动启动的情况下 `data` 路径问题。
>
> 建议要么全部放在一个 docker-compose.yml 里面，或者全部手动启动。

如果选择手动启动前后端，操作如下：

#### 启动依赖

> [!WARNING]
> 注意，你仍然需要按照「部署运行」一节中配置好 `.env` 文件

```shell
# 启动 MongoDB，redis 等依赖
> docker-compose -f docker-compose-dev.yml up
```

#### 后端编译和启动

后端依赖：`jdk-17`，基于 `jdk-17` 的 `kotlin`。
```shell
> cd server
> ./gradlew installDist  # 编译整个后端
# 编译的结果为：./build/install/wneg/bin/wneg
# 启动 wneg 需要从环境变量中提供 JWT token 等
# 需要使用「部署运行」一节中的 .env 中配置的数据。
> ./build/install/wneg/bin/wneg  # 启动后端，默认监听 localhost:8081
```

> [!NOTE]
> 注意，后端会自动在 `启动目录` 下创建 data 文件夹，用于存放上传 epub 等数据。
>
> 因此需要修改对应前端的 `Caddyfile`

#### 前端编译和启动

前端的编译运行很简单：

```shell
> cd web
> pnpm install
> pnpm build
```

> [!WARNING]
> 注意，`pnpm dev` 虽然能启动，但是对于所有的文件下载行为将会报错。
>
> 如果开发不涉及到文件下载的话，可以直接使用 `pnpm dev`。
>
> 否则，请参见下面的 `文件下载` 一节，利用 `caddy` 启动编译好的前端。

##### 文件下载等

对于下载等行为，前端（或者严格说是 nginx/caddy 这一层）需要知道去哪里获取数据。

修改 `web/Caddyfile` 文件，假设修改后的文件为 `Caddyfile.dev`

<details>
<summary>
`Caddyfile.dev` 内容，点击展开
</summary>

```caddyfile
:8080 {                         # 将原本的 80 端口修改为 8080
	encode gzip

	handle {
		root * ./dist               # 使用 dist（pnpm build）的结果
		file_server
		route {
			try_files {path} /
			header / Cache-Control "no-cache,no-store,max-age=0,must-revalidate"
		}
		header /assets/* Cache-Control "public, max-age=7776000"
		header /*.png Cache-Control "public, max-age=7776000"
		header /*.svg Cache-Control "public, max-age=7776000"
		header /*.webp Cache-Control "public, max-age=7776000"
	}

	@filename {
		query filename=*
	}

	handle_path /api* {
		reverse_proxy 0.0.0.0:8081    # 将 api 路径反代到后端服务器
	}

	handle_path /files-temp* {            # 文件下载服务
		root * ../server/data/files-temp    # 修改这个路径，指向 server 启动路径下的 data 文件夹
		file_server
		header @filename Content-Disposition "attachment; filename=\"{http.request.uri.query.filename}\""
	}

	handle_path /files-extra* {
		root * ../server/data/files-extra   # 修改这个路径，指向 server 启动路径下的 data 文件夹
		file_server
		header /*.png Cache-Control "public, max-age=7776000"
		header /*.webp Cache-Control "public, max-age=7776000"
	}
}
```
</details>

`Cadddyfile.dev` 准备好后，执行如下命令启动 `Cadddy` 即可。
```shell
> caddy run -c Caddyfile.dev --adapter caddyfile
```
### 其他说明

`docker` 启动的 `mongodb`是未加密的，`DataGrip` 等应用选择 `noauth` 即可连上。

Collections 不会自动初始化，当上传或者建立相应的数据后才会添加对应的 Collections。

用户建立需要绕过 Email 验证，可以先注释掉相应的验证模块。

建立好第一个帐号后，通过 `DataGrip` 连上 `mongodb`，手动修改建立的帐号的 role 为 admin。
