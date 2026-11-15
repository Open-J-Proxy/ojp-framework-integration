class ShopRouter:
    """Keep local relationships together and checkout's catalog IDs unconstrained."""

    aliases = {"catalog": "default", "checkout": "checkout"}

    def db_for_read(self, model, **hints):
        return self.aliases.get(model._meta.app_label)

    def db_for_write(self, model, **hints):
        return self.aliases.get(model._meta.app_label)

    def allow_relation(self, obj1, obj2, **hints):
        first = self.aliases.get(obj1._meta.app_label)
        second = self.aliases.get(obj2._meta.app_label)
        if first is not None or second is not None:
            return first == second
        return None

    def allow_migrate(self, db, app_label, model_name=None, **hints):
        alias = self.aliases.get(app_label)
        if alias is not None:
            return db == alias
        return None
