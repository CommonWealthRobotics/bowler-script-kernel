"""Check the actual single-body 3MF output before a CSG loader can clean it."""
import json, os, sys, traceback, zipfile
import xml.etree.ElementTree as ET
import Mesh

try:
    path = os.environ['CHECK_3MF']
    mesh = Mesh.Mesh(path)
    with zipfile.ZipFile(path) as archive:
        entry = next(n for n in archive.namelist() if n.lower().endswith('.model'))
        model = ET.fromstring(archive.read(entry))
    namespace = {'m': model.tag.split('}')[0][1:]}
    build_items = len(model.findall('m:build/m:item', namespace))
    report = {'file': path, 'facets': mesh.CountFacets, 'vertices': mesh.CountPoints,
              'solid': mesh.isSolid(), 'self_intersections': mesh.hasSelfIntersections(),
              'non_manifold_edges': mesh.hasNonManifolds(), 'components': mesh.countComponents(),
              'build_items': build_items}
    report['pass'] = (report['facets'] > 0 and report['solid']
                      and not report['self_intersections'] and not report['non_manifold_edges']
                      and report['components'] == 1 and build_items == 1)
    with open(os.environ['CHECK_JSON'], 'w') as handle:
        json.dump(report, handle, indent=2)
    print(json.dumps(report))
    code = 0 if report['pass'] else 1
except BaseException:
    traceback.print_exc()
    code = 2
sys.stdout.flush()
sys.stderr.flush()
os._exit(code)
