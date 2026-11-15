from datetime import datetime

from django.db import models


class Order(models.Model):
    user_id = models.BigIntegerField()
    order_date = models.DateTimeField(default=datetime.now, null=True, blank=True)

    class Meta:
        db_table = "django_orders"
        ordering = ["id"]


class OrderItem(models.Model):
    order = models.ForeignKey(Order, related_name="order_items", on_delete=models.CASCADE)
    product_id = models.BigIntegerField()
    quantity = models.IntegerField(default=0)

    class Meta:
        db_table = "django_order_items"
        ordering = ["id"]
