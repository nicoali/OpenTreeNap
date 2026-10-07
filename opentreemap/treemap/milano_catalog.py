"""Resolve exact catalog identities without duplicating the OTN seed list."""


def resolve_species(model, instance, user, taxon, code):
    species = model.objects.filter(instance=instance, otm_code=code).first()
    if species is not None:
        return species
    identity = dict(instance=instance,
                    common_name=' '.join(v for v in taxon.values() if v),
                    other_part_of_name='', **taxon)
    # OTN's database identity excludes otm_code. create_instance seeds the
    # native catalog, which can already contain this exact row (e.g. Magnolia).
    species = model.objects.filter(**identity).first()
    if species is None:
        species = model(otm_code=code, **identity)
        species.save_with_user(user)
    return species
