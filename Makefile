build-frontend:
	cd frontend && npm run build

build:
	docker-compose -f docker-compose.dev.yml build --progress plain

dev:
	docker-compose -f docker-compose.dev.yml up -d

prod:
	docker-compose -f docker-compose.prod.yml up -d

test-cli:
	python backend/script/cli.py "https://ncode.syosetu.com/n0833hi" --epub --txt --zh --epub-mixed --txt-mixed --start 23 --end 23