build-frontend:
	cd frontend && npm run build

build:
	docker-compose build --progress plain

dev:
	docker-compose -f docker-compose.yml -f docker-compose.dev.yml up -d