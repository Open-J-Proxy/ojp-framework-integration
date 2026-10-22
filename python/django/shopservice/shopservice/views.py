"""Anonymous sample REST API, matching the existing shopservice contracts."""

import json
from functools import wraps

from django.core.exceptions import RequestDataTooBig
from django.http import HttpResponse, HttpResponseNotAllowed, JsonResponse
from django.views.decorators.csrf import csrf_exempt

from .models import Order, OrderItem, Product, Review, User
from .repositories import Repositories
from .serialization import (
    BadRequest, as_object, nested_id, opt_datetime, opt_int, opt_str,
    order_item_json, order_json, page_json, page_request, product_json,
    required_decimal, required_str, review_json, user_json,
)

SERIALIZERS = {"users": user_json, "products": product_json, "reviews": review_json, "orders": order_json}


def endpoint(view):
    @csrf_exempt
    @wraps(view)
    def wrapped(request, **kwargs):
        try:
            return view(request, Repositories.create(), **kwargs)
        except (BadRequest, json.JSONDecodeError, UnicodeDecodeError, RequestDataTooBig):
            return HttpResponse(status=400)
    return wrapped


def body(request):
    try:
        return as_object(json.loads(request.body))
    except ValueError as exc:
        raise BadRequest("JSON body") from exc


def ok(value):
    return JsonResponse(value, safe=isinstance(value, dict))


def path_id(value):
    result = opt_int({"id": value}, "id")
    if result is None:
        raise BadRequest("id")
    return result


def read_items(data):
    raw = data.get("orderItems")
    if raw is None:
        return None
    if not isinstance(raw, list):
        raise BadRequest("orderItems")
    items = []
    for element in raw:
        element = as_object(element)
        product_id = opt_int(element, "productId")
        if product_id is None:
            raise BadRequest("productId")
        items.append(OrderItem(product_id=product_id, quantity=integer_field(element, "quantity")))
    return items


def integer_field(data, key):
    value = opt_int(data, key) or 0
    if not -(2**31) <= value < 2**31:
        raise BadRequest(key)
    return value


def valid_items(repos, items):
    return items is None or bool(items) and all(repos.products.exists_by_id(item.product_id) for item in items)


def create_entity(resource, data, repos):
    if resource == "users":
        return repos.users.save(User(
            username=required_str(data, "username"),
            email=required_str(data, "email"),
            created_at=opt_datetime(data, "createdAt"),
        ))
    if resource == "products":
        return repos.products.save(Product(name=required_str(data, "name"), price=required_decimal(data, "price")))
    if resource == "reviews":
        user_id, product_id = nested_id(data, "user"), nested_id(data, "product")
        if not repos.users.exists_by_id(user_id) or not repos.products.exists_by_id(product_id):
            raise BadRequest("review references")
        return repos.reviews.save(Review(
            user_id=user_id, product_id=product_id,
            rating=integer_field(data, "rating"), comment=opt_str(data, "comment"),
        ))
    user_id = opt_int(data, "userId")
    if not repos.users.exists_by_id(user_id):
        raise BadRequest("userId")
    items = read_items(data)
    if not valid_items(repos, items):
        raise BadRequest("orderItems")
    order = Order(user_id=user_id)
    order_date = opt_datetime(data, "orderDate")
    if order_date is not None:
        order.order_date = order_date
    return repos.orders.save_with_items(order, items)


def update_entity(resource, existing, data, repos):
    if resource == "users":
        existing.username = required_str(data, "username")
        existing.email = required_str(data, "email")
    elif resource == "products":
        existing.name = required_str(data, "name")
        existing.price = required_decimal(data, "price")
    elif resource == "reviews":
        user_id, product_id = nested_id(data, "user"), nested_id(data, "product")
        if repos.users.exists_by_id(user_id):
            existing.user_id = user_id
        if repos.products.exists_by_id(product_id):
            existing.product_id = product_id
        existing.rating = integer_field(data, "rating")
        existing.comment = opt_str(data, "comment")
        # Updating IDs invalidates any select_related objects cached on reads.
        return repos.reviews.find_by_id(repos.reviews.save(existing).pk)
    else:
        user_id = opt_int(data, "userId")
        if repos.users.exists_by_id(user_id):
            existing.user_id = user_id
        order_date = opt_datetime(data, "orderDate")
        if order_date is not None:
            existing.order_date = order_date
        items = read_items(data)
        return repos.orders.save_with_items(existing, items if valid_items(repos, items) else None)
    return getattr(repos, resource).save(existing)


@endpoint
def collection(request, repos, resource):
    repo, serialize = getattr(repos, resource), SERIALIZERS[resource]
    if request.method == "GET":
        page = page_request(request.GET.get("page"), request.GET.get("size"))
        criteria = {}
        if resource == "reviews":
            user_id = opt_int({"userId": request.GET.get("userId")}, "userId")
            product_id = opt_int({"productId": request.GET.get("productId")}, "productId")
            if user_id is not None:
                criteria["user_id"] = user_id
            elif product_id is not None:
                criteria["product_id"] = product_id
        items, total = repo.find_page(page, **criteria)
        return ok(page_json([serialize(item) for item in items], total, page))
    if request.method == "POST":
        return ok(serialize(create_entity(resource, body(request), repos)))
    if request.method == "PUT" and resource == "products":
        product_id = opt_int(body(request), "id")
        response = HttpResponse(status=201)
        response["Location"] = "/products/" + (str(product_id) if product_id is not None else "null")
        return response
    return HttpResponseNotAllowed(["GET", "POST", "PUT"] if resource == "products" else ["GET", "POST"])


@endpoint
def detail(request, repos, resource, entity_id):
    entity_id = path_id(entity_id)
    if request.method not in ("GET", "PUT", "DELETE"):
        return HttpResponseNotAllowed(["GET", "PUT", "DELETE"])
    repo, serialize = getattr(repos, resource), SERIALIZERS[resource]
    existing = repo.find_by_id(entity_id)
    if existing is None:
        return HttpResponse(status=404)
    if request.method == "GET":
        return ok(serialize(existing))
    if request.method == "DELETE":
        repo.delete(existing)
        return HttpResponse(status=204)
    return ok(serialize(update_entity(resource, existing, body(request), repos)))


@endpoint
def order_items(request, repos, order_id, item_id=None):
    order_id = path_id(order_id)
    if item_id is None:
        if request.method == "GET":
            return ok([order_item_json(item) for item in repos.order_items.find_by_order_id(order_id)])
        if request.method != "POST":
            return HttpResponseNotAllowed(["GET", "POST"])
        if not repos.orders.exists_by_id(order_id):
            raise BadRequest("orderId")
        data = body(request)
        product_id = opt_int(data, "productId")
        if not repos.products.exists_by_id(product_id):
            raise BadRequest("productId")
        return ok(order_item_json(repos.order_items.save(OrderItem(
            order_id=order_id, product_id=product_id, quantity=integer_field(data, "quantity"),
        ))))
    item_id = path_id(item_id)
    if request.method not in ("GET", "PUT", "DELETE"):
        return HttpResponseNotAllowed(["GET", "PUT", "DELETE"])
    if request.method == "PUT":
        data = body(request)
        product_id = opt_int(data, "productId")
        if product_id is not None and not repos.products.exists_by_id(product_id):
            raise BadRequest("productId")
    existing = repos.order_items.find_by_id(item_id)
    if existing is None or existing.order_id != order_id:
        return HttpResponse(status=404)
    if request.method == "GET":
        return ok(order_item_json(existing))
    if request.method == "DELETE":
        repos.order_items.delete(existing)
        return HttpResponse(status=204)
    if product_id is not None:
        existing.product_id = product_id
    existing.quantity = integer_field(data, "quantity")
    return ok(order_item_json(repos.order_items.save(existing)))
