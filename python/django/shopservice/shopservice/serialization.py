"""Spring-compatible request parsing and JSON representations."""

from datetime import datetime
from decimal import Decimal, InvalidOperation

from .repositories import PageRequest


class BadRequest(Exception):
    pass


def as_object(body):
    if not isinstance(body, dict):
        raise BadRequest("JSON object expected")
    return body


def opt_int(body, key):
    value = body.get(key)
    if value is None:
        return None
    if isinstance(value, bool):
        raise BadRequest(key)
    if isinstance(value, int):
        result = value
    elif isinstance(value, float) and value.is_integer():
        result = int(value)
    elif isinstance(value, str):
        try:
            result = int(value)
        except ValueError as exc:
            raise BadRequest(key) from exc
    else:
        raise BadRequest(key)
    if not -(2**63) <= result < 2**63:
        raise BadRequest(key)
    return result


def opt_str(body, key):
    value = body.get(key)
    if value is not None and not isinstance(value, str):
        raise BadRequest(key)
    return value


def required_str(body, key):
    value = opt_str(body, key)
    if value is None or len(value) > 255:
        raise BadRequest(key)
    return value


def required_decimal(body, key):
    value = body.get(key)
    if value is None or isinstance(value, bool) or not isinstance(value, (str, int, float)):
        raise BadRequest(key)
    try:
        result = Decimal(str(value))
    except InvalidOperation as exc:
        raise BadRequest(key) from exc
    if not result.is_finite() or abs(result) >= Decimal("100000000000000000"):
        raise BadRequest(key)
    return result


def opt_datetime(body, key):
    value = body.get(key)
    if value is None:
        return None
    if not isinstance(value, str):
        raise BadRequest(key)
    try:
        return datetime.fromisoformat(value)
    except ValueError as exc:
        raise BadRequest(key) from exc


def nested_id(body, key):
    value = body.get(key)
    return opt_int(value, "id") if isinstance(value, dict) else None


def page_request(page, size):
    number, page_size = 0, 20
    try:
        if page is not None and int(page) >= 0:
            number = int(page)
    except ValueError:
        pass
    try:
        if size is not None and int(size) >= 1:
            page_size = min(int(size), 2000)
    except ValueError:
        pass
    return PageRequest(number, page_size)


def user_json(user):
    return {
        "id": user.id,
        "username": user.username,
        "email": user.email,
        "createdAt": user.created_at.isoformat() if user.created_at is not None else None,
    }


def product_json(product):
    return {"id": product.id, "name": product.name, "price": float(product.price)}


def review_json(review):
    return {
        "id": review.id,
        "user": user_json(review.user),
        "product": product_json(review.product),
        "rating": review.rating,
        "comment": review.comment,
    }


def order_item_json(item):
    return {"id": item.id, "productId": item.product_id, "quantity": item.quantity}


def order_json(order):
    return {
        "id": order.id,
        "userId": order.user_id,
        "orderItems": [order_item_json(item) for item in order.order_items.all()],
        "orderDate": order.order_date.isoformat() if order.order_date is not None else None,
    }


def page_json(content, total, page):
    total_pages = (total + page.size - 1) // page.size
    sort = {"sorted": False, "unsorted": True, "empty": True}
    return {
        "content": list(content),
        "pageable": {
            "pageNumber": page.page,
            "pageSize": page.size,
            "sort": sort,
            "offset": page.offset,
            "paged": True,
            "unpaged": False,
        },
        "totalElements": total,
        "totalPages": total_pages,
        "last": page.page + 1 >= total_pages,
        "size": page.size,
        "number": page.page,
        "sort": sort,
        "first": page.page == 0,
        "numberOfElements": len(content),
        "empty": len(content) == 0,
    }
