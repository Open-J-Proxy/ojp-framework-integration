from django.urls import re_path

from . import views

urlpatterns = [
    re_path(r"^(?P<resource>users|products|reviews|orders)$", views.collection),
    re_path(r"^(?P<resource>users|products|reviews|orders)/(?P<entity_id>[^/]+)$", views.detail),
    re_path(r"^orders/(?P<order_id>[^/]+)/items$", views.order_items),
    re_path(r"^orders/(?P<order_id>[^/]+)/items/(?P<item_id>[^/]+)$", views.order_items),
]
