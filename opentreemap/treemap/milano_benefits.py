"""Validated mapping for the historical Milano i-Tree Eco result fields."""
import json
import math


LEGACY_FIELD = 'Dati storici Milano'
SOURCE_FIELD = 'Fonte benefici ecosistemici'
REFERENCE_FIELD = 'Riferimento benefici ecosistemici'

# The ArcGIS/Excel names are truncated shapefile field names.  Units are
# corroborated by the accompanying 2018 Milano i-Tree Eco study and by the
# magnitude/totals in the source data.
BENEFIT_FIELDS = (
    ('carbon_sto', 'Carbonio immagazzinato (kg)'),
    ('carbon_s_1', 'Valore carbonio immagazzinato (EUR)'),
    ('gross_carb', 'Carbonio sequestrato (kg/anno)'),
    ('gross_ca_1', 'Valore carbonio sequestrato (EUR/anno)'),
    ('avoided_ru', 'Deflusso evitato (m3/anno)'),
    ('avoided_1', 'Valore deflusso evitato (EUR/anno)'),
    ('pollution_', 'Inquinanti rimossi (g/anno)'),
    ('pollutio_1', 'Valore inquinanti rimossi (EUR/anno)'),
    ('structural', 'Valore strutturale (EUR)'),
    ('total_annu', 'Benefici annuali totali (EUR/anno)'),
)

FIELD_DESCRIPTIONS = {
    'Carbonio immagazzinato (kg)':
        'Stock di carbonio stimato nello studio i-Tree Eco storico di Milano.',
    'Valore carbonio immagazzinato (EUR)':
        'Valore monetario dello stock di carbonio nello studio storico.',
    'Carbonio sequestrato (kg/anno)':
        'Sequestro annuo di carbonio stimato nello studio storico.',
    'Valore carbonio sequestrato (EUR/anno)':
        'Valore monetario annuo del carbonio sequestrato nello studio storico.',
    'Deflusso evitato (m3/anno)':
        'Deflusso superficiale evitato annualmente nello studio storico.',
    'Valore deflusso evitato (EUR/anno)':
        'Valore monetario annuo del deflusso evitato nello studio storico.',
    'Inquinanti rimossi (g/anno)':
        'Massa annua totale di inquinanti rimossi nello studio storico.',
    'Valore inquinanti rimossi (EUR/anno)':
        'Valore monetario annuo degli inquinanti rimossi nello studio storico.',
    'Valore strutturale (EUR)':
        'Valore strutturale stimato dell\'albero nello studio storico.',
    'Benefici annuali totali (EUR/anno)':
        'Somma di carbonio sequestrato, deflusso evitato e inquinanti rimossi.',
    SOURCE_FIELD:
        'Fonte dello snapshot; il valore non e una nuova stima sul censimento 2024.',
    REFERENCE_FIELD:
        'Anno dello studio e scenario ambientale usato dal modello storico.',
}

SOURCE = 'Comune di Milano / i-Tree Eco, archivio storico'
REFERENCE = 'Studio 2018; dati ambientali ARPA Lombardia 2011'


def parse_historical_benefits(raw):
    """Return display-name/value pairs from one stored legacy snapshot."""
    if isinstance(raw, str):
        raw = json.loads(raw)
    if not isinstance(raw, dict):
        raise ValueError('Historical Milano payload must be an object')
    benefits = raw.get('benefits_unvalidated')
    if not isinstance(benefits, dict):
        raise ValueError('Historical Milano payload has no benefit values')

    result = {}
    for source_name, display_name in BENEFIT_FIELDS:
        value = benefits.get(source_name)
        try:
            value = float(value)
        except (TypeError, ValueError):
            raise ValueError('Invalid historical benefit: '+source_name)
        if not math.isfinite(value) or value < 0:
            raise ValueError('Invalid historical benefit: '+source_name)
        result[display_name] = value

    expected = (result['Valore carbonio sequestrato (EUR/anno)'] +
                result['Valore deflusso evitato (EUR/anno)'] +
                result['Valore inquinanti rimossi (EUR/anno)'])
    if abs(expected-result['Benefici annuali totali (EUR/anno)']) > 0.02:
        raise ValueError('Historical annual benefit components do not match total')
    result[SOURCE_FIELD] = SOURCE
    result[REFERENCE_FIELD] = REFERENCE
    return result

