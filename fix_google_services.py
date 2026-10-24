import json

with open('app/google-services.json', 'r') as f:
    data = json.load(f)

clients = data['client']
base_client = clients[0]

packages_to_add = [
    "three.haddad.smartnotas.gh",
    "three.haddad.smartnotas.offline",
    "three.haddad.smartnotas.offline.dev",
    "three.haddad.smartnotas.dev"
]

existing_packages = [c['client_info']['android_client_info']['package_name'] for c in clients]

for pkg in packages_to_add:
    if pkg not in existing_packages:
        import copy
        new_client = copy.deepcopy(base_client)
        new_client['client_info']['android_client_info']['package_name'] = pkg
        data['client'].append(new_client)

with open('app/google-services.json', 'w') as f:
    json.dump(data, f, indent=2)

