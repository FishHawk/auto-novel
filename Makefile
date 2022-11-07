build-frontend:
	cd frontend && npm run build

build:
	docker-compose build --progress plain

dev:
	docker-compose -f docker-compose.dev.yml up -d

prod:
	docker-compose -f docker-compose.prod.yml up -d

test-cli:
	python backend/script/cli.py "https://ncode.syosetu.com/n0833hi" --epub --txt --zh --epub-mixed --txt-mixed