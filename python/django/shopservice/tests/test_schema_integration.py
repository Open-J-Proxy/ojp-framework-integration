"""Exercise Django's PostgreSQL schema editor and catalog over live OJP."""

from io import StringIO
from datetime import date, datetime, time, timedelta
from decimal import Decimal
from uuid import UUID, uuid4

import pytest
from django.core.management import call_command
from django.db import IntegrityError, connections, models, transaction
from django.test.utils import isolate_apps


pytestmark = pytest.mark.integration

HOSTILE_DEFAULT = "O'Reilly\\path %s ? ); DROP TABLE never_a_table; --"


@pytest.fixture(params=["default", "checkout"])
def schema_table(request):
    connection = connections[request.param]
    assert connection.vendor == "postgresql"
    assert connection.settings_dict["ENGINE"] == "postgresql_odbc"
    connection.ensure_connection()
    table = f"ojp_schema_{uuid4().hex[:16]}"
    renamed = f"{table}_renamed"
    with isolate_apps():
        class Probe(models.Model):
            label = models.CharField(max_length=32, unique=True)
            amount = models.DecimalField(max_digits=10, decimal_places=2, default=0)
            enabled = models.BooleanField(default=True)

            class Meta:
                app_label = "odbc_contract"
                db_table = table

        try:
            yield connection, Probe, renamed
        finally:
            # A failed statement may leave the server transaction aborted. Close
            # that connection before cleaning up either side of a failed rename.
            connection.close()
            try:
                with connection.cursor() as cursor:
                    for name in (table, renamed):
                        cursor.execute(f"DROP TABLE IF EXISTS {connection.ops.quote_name(name)} CASCADE")
            finally:
                connection.close()


def constraints(connection, table):
    with connection.cursor() as cursor:
        return connection.introspection.get_constraints(cursor, table)


def test_live_schema_editor_create_alter_constraints_index_rename_drop(schema_table):
    connection, model, renamed = schema_table
    alias = connection.alias
    with connection.schema_editor() as editor:
        editor.create_model(model)
    with connection.cursor() as cursor:
        description = connection.introspection.get_table_description(cursor, model._meta.db_table)
    types = {
        field.name: connection.introspection.get_field_type(field.type_code, field)
        for field in description
    }
    assert types == {
        "id": "BigAutoField", "label": "CharField",
        "amount": "DecimalField", "enabled": "BooleanField",
    }
    initial = constraints(connection, model._meta.db_table)
    assert any(info["primary_key"] and info["columns"] == ["id"] for info in initial.values())
    assert any(info["unique"] and info["columns"] == ["label"] for info in initial.values())
    model.objects.using(alias).create(label="before")

    added = models.CharField(max_length=128, default=HOSTILE_DEFAULT)
    added.set_attributes_from_name("note")
    added.model = model
    with connection.schema_editor() as editor:
        editor.add_field(model, added)
    with connection.cursor() as cursor:
        cursor.execute(f'SELECT "note" FROM {connection.ops.quote_name(model._meta.db_table)}')
        assert cursor.fetchone() == (HOSTILE_DEFAULT,)

    changed = models.CharField(max_length=192, default=HOSTILE_DEFAULT, db_column="renamed_note")
    changed.set_attributes_from_name("note")
    changed.model = model
    with connection.schema_editor() as editor:
        editor.alter_field(model, added, changed)
    check = models.CheckConstraint(condition=models.Q(amount__gte=0), name=f"{model._meta.db_table}_positive")
    index = models.Index(fields=["amount"], name=f"{model._meta.db_table}_amount")
    with connection.schema_editor() as editor:
        editor.add_constraint(model, check)
        editor.add_index(model, index)
    current = constraints(connection, model._meta.db_table)
    assert current[check.name]["check"]
    assert current[index.name]["index"]
    assert current[index.name]["columns"] == ["amount"]
    # Catch outside atomic so Django rolls the failed PostgreSQL transaction back.
    with pytest.raises(IntegrityError):
        with transaction.atomic(using=alias):
            model.objects.using(alias).filter(label="before").update(amount=-1)
    assert model.objects.using(alias).count() == 1

    output = StringIO()
    call_command("inspectdb", model._meta.db_table, database=alias, stdout=output)
    inspected = output.getvalue()
    assert "models.DecimalField(" in inspected
    assert "models.BooleanField(" in inspected
    assert "renamed_note" in inspected
    assert "unique=True" in inspected
    assert model._meta.db_table in inspected

    with connection.schema_editor() as editor:
        editor.remove_constraint(model, check)
        editor.remove_index(model, index)
        editor.remove_field(model, changed)
        editor.alter_db_table(model, model._meta.db_table, renamed)
    model._meta.db_table = renamed
    assert check.name not in constraints(connection, renamed)
    assert index.name not in constraints(connection, renamed)
    assert model.objects.using(alias).get().label == "before"
    with connection.schema_editor() as editor:
        editor.delete_model(model)
    assert renamed not in connection.introspection.table_names()


def test_live_typed_orm_round_trip_and_description(schema_table):
    connection, model, _ = schema_table
    for name, field in [
        ("day", models.DateField()),
        ("clock", models.TimeField()),
        ("moment", models.DateTimeField()),
        ("identifier", models.UUIDField()),
        ("payload", models.JSONField()),
        ("data", models.BinaryField()),
        ("ratio", models.FloatField()),
        ("elapsed", models.DurationField()),
    ]:
        field.contribute_to_class(model, name)
    with connection.schema_editor() as editor:
        editor.create_model(model)
    values = {
        "label": HOSTILE_DEFAULT[:32],
        "amount": Decimal("12.50"),
        "enabled": False,
        "day": date(2024, 1, 15),
        "clock": time(10, 30, 0, 123456),
        "moment": datetime(2024, 1, 15, 10, 30, 0, 123456),
        "identifier": UUID("12345678-1234-5678-1234-567812345678"),
        "payload": {"quote": "O'Reilly %s ?", "list": [1, False, None]},
        "data": b"\x00O'Reilly\xff",
        "ratio": 1.25,
        "elapsed": timedelta(days=2, hours=3, minutes=4, seconds=5),
    }
    saved = model.objects.using(connection.alias).create(**values)
    loaded = model.objects.using(connection.alias).get(pk=saved.pk)
    assert isinstance(saved.pk, int)
    for name, expected in values.items():
        actual = getattr(loaded, name)
        if name == "data":
            actual = bytes(actual)
        assert actual == expected, name
    durations = model.objects.using(connection.alias)
    assert durations.aggregate(total=models.Sum("elapsed")) == {"total": values["elapsed"]}
    assert durations[:1].aggregate(total=models.Sum("elapsed")) == {"total": values["elapsed"]}
    durations.filter(pk=saved.pk).update(elapsed=timedelta(days=10))
    another = dict(values, label="short", identifier=uuid4(), elapsed=timedelta(days=2))
    durations.create(**another)
    for queryset in (
        durations.values_list("elapsed", flat=True).order_by("elapsed"),
        durations.values_list("elapsed", flat=True).order_by("elapsed").distinct(),
        durations.values_list("elapsed", flat=True).order_by("elapsed").distinct("elapsed"),
    ):
        assert list(queryset) == [timedelta(days=2), timedelta(days=10)]
    with connection.cursor() as cursor:
        description = connection.introspection.get_table_description(cursor, model._meta.db_table)
    field_types = {
        field.name: connection.introspection.get_field_type(field.type_code, field)
        for field in description
    }
    assert {name: field_types[name] for name in values if name not in {"label", "amount", "enabled"}} == {
        "day": "DateField", "clock": "TimeField", "moment": "DateTimeField",
        "identifier": "UUIDField", "payload": "JSONField", "data": "BinaryField",
        "ratio": "FloatField", "elapsed": "DurationField",
    }
