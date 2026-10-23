from django.db import models


class User(models.Model):
    username = models.CharField(max_length=255)
    email = models.CharField(max_length=255)
    created_at = models.DateTimeField(null=True, blank=True)

    class Meta:
        db_table = "django_users"
        ordering = ["id"]


class Product(models.Model):
    name = models.CharField(max_length=255)
    price = models.DecimalField(max_digits=19, decimal_places=2)

    class Meta:
        db_table = "django_products"
        ordering = ["id"]


class Review(models.Model):
    user = models.ForeignKey(User, on_delete=models.PROTECT)
    product = models.ForeignKey(Product, on_delete=models.PROTECT)
    rating = models.IntegerField(default=0)
    comment = models.TextField(null=True, blank=True)

    class Meta:
        db_table = "django_reviews"
        ordering = ["id"]
