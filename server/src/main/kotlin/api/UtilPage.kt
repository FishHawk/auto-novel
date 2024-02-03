package api

fun validatePageNumber(page: Int) {
    if (page < 0) {
        throwBadRequest("页码不应该小于0")
    }
}

fun validatePageSize(pageSize: Int, max: Int = 100) {
    if (pageSize < 1) {
        throwBadRequest("每页数据量不应该小于1")
    }
    if (pageSize > max) {
        throwBadRequest("每页数据量不应该大于${max}")
    }
}
