import requests
import subprocess
import time
import random
from bs4 import BeautifulSoup

def getWikiTitle():
    # Get title of the active Wikipedia page
    global activePath
    soup = BeautifulSoup(subprocess.check_output("chrome-cli source", shell=True), features="lxml")
    title = soup.find('h1')
    if type(title) == type(None):
        return activePath[-1]
    else:
        return title.text

def query(request):
    # Source: https://www.mediawiki.org/wiki/API:Continue
    global numrequests
    
    request['action'] = 'query'
    request['format'] = 'json'
    lastContinue = {}
    while True:
        # Clone original request
        req = request.copy()
        # Modify it with the values returned in the 'continue' section of the last result.
        req.update(lastContinue)
        # Call API
        result = requests.get('https://en.wikipedia.org/w/api.php', params=req).json()
        numrequests += 1
        if 'error' in result:
            raise Exception(result['error'])
        if 'warnings' in result:
            print(result['warnings'])
        if 'query' in result:
            yield result['query']
        if 'continue' not in result:
            break
        lastContinue = result['continue']

def getLinks(q: dict):
    x = {}
    for i in query(q):
        x.update(i['pages'])
    x = dict(filter(lambda z: (int(z[0]) > 0), x.items())) # remove invalid values

    a = {int(k):v for k, v in x.items()} 
    a = dict(sorted(a.items(), key=lambda item: -item[1]['revisions'][0]['size'] + item[0]))

    return a

def checkPath(x, y):
    global forward, backward

    options = list(x.keys())
    front = x[options[random.randint(0, min(5, len(options)))]]
    print(f'⏩ Advancing to {front["title"]} (id = {front["pageid"]}, size = {front["revisions"][0]["size"]}) \n{[x[i]["title"] for i in options[:min(5, len(options))]]}')

    k = getLinks({'generator': 'links', 'gplnamespace':'0', 'gpllimit': 'max', 'prop':'revisions', 'rvprop':'size', 'redirects':'1', 'titles': front['title']})
    forward.append((forward[-1][0]+[front['title']], k))

    options = list(y.keys())
    back = y[options[random.randint(0, min(5, len(options)))]]
    print(f'⏪ Backtracking to {back["title"]} (id = {back["pageid"]}, size = {back["revisions"][0]["size"]}) \n{[y[i]["title"] for i in options[:min(5, len(options))]]}')

    m = getLinks({'generator': 'linkshere', 'glhnamespace':'0', 'glhlimit': 'max', 'prop':'revisions', 'rvprop':'size', 'redirects':'1', 'titles': back['title']})
    backward.append((backward[-1][0]+[back['title']], m))

    for i in forward:
        for j in backward[::-1]:
            q = list(set(i[1]).intersection(set(j[1])))
            if q != []:
                return i[0] + [i[1][q[0]]['title']] + j[0][::-1]
    return None

soup = BeautifulSoup(subprocess.check_output("chrome-cli source", shell=True), features="lxml")
START = getWikiTitle()
FINISH = soup.select('.PlasmicWiki_slotTargetDestination__i4_cc div:first-child')[0].text
print(f"Starting a search from {START} to {FINISH}\n")

stime = time.time()
numrequests = 0

'''
# Request paths from the 6-degrees-of-wikipedia API
# https://www.sixdegreesofwikipedia.com/
# https://github.com/jwngr/sdow
headers = {'Content-Type': 'application/json'}
json_data = {'source': START, 'target': FINISH}
response = json.loads(requests.post('https://api.sixdegreesofwikipedia.com/paths', headers=headers, json=json_data).text)
if ('error' in response.keys()):
    print(response)

# Process paths into lists of URLs
paths = []
for path in response['paths']:
    paths.append([response['pages'][str(page)]['url'] for page in path[1:]])

activePath = paths[random.randint(0, len(paths)-1)]
print(f"Guiding on random path out of {len(paths)} option(s)")
print(f'https://www.sixdegreesofwikipedia.com/?source={requests.utils.quote(START)}&target={requests.utils.quote(FINISH)}\n')
'''

#a = json.loads(requests.get(f"https://en.wikipedia.org/w/api.php?action=query&format=json&generator=links&formatversion=2&gplnamespace=0&gpllimit=max&gpldir=ascending&titles={START}").text)

a = getLinks({'generator': 'links', 'gplnamespace':'0', 'gpllimit': 'max', 'prop':'revisions', 'rvprop':'size', 'redirects':'1', 'titles': START})
b = getLinks({'generator': 'linkshere', 'glhnamespace':'0', 'glhlimit': 'max', 'prop':'revisions', 'rvprop':'size', 'redirects':'1', 'titles': FINISH})


forward = [([], a)]
backward = [([FINISH], b)]
q = list(set(forward[0][1]).intersection(backward[0][1]))
if q != []:
    activePath = [a[q[0]]['title']] + [FINISH]
else:
    for o in range(10):
       activePath = checkPath(forward[-1][1], backward[-1][1])
       if activePath != None:
           break

print(f'\nPATH FOUND in {time.time()-stime:.2f} seconds. ({numrequests} API calls)')
print(START, '->', ' -> '.join(activePath))
print(f'https://www.sixdegreesofwikipedia.com/?source={requests.utils.quote(START)}&target={requests.utils.quote(FINISH)}\n')
#activePath = ['https://en.wikipedia.org/wiki/'+x.replace(' ', '_') for x in activePath]

# Ok, Google
# Play https://www.youtube.com/watch?v=kpnW68Q8ltc

for i in range(len(activePath)):
    currentPage = getWikiTitle()
    print(f'{i+1}/{len(activePath)} ', currentPage, '->', activePath[i])
    
    # Get the list of redirects to the page we are guiding on and add them to the search list
    try:
        redirects = list(query({'generator':'redirects', 'grdlimit':'max', 'grdnamespace': '0', 'prop':'info', 'inprop':'url', 'titles':activePath[i]}))[0]['pages']
        redirects = ['https://en.wikipedia.org/wiki/'+activePath[i].replace(' ', '_')] + [x['fullurl'] for x in list(redirects.values())]
    except:
        redirects = ['https://en.wikipedia.org/wiki/'+activePath[i].replace(' ', '_')]

    # Open all collapsible elements
    subprocess.run('chrome-cli execute "for (let item of Array.from(document.querySelectorAll(\'span.mw-collapsible-text\')).filter(el => el.textContent === \'show\')) {item.click()}"', shell=True)

    # Show user next link
    findLink = f"l = document.links;for (let i=0; i<l.length; i++) {{if({redirects}.includes(l[i].href.split('#')[0])) {{ l[i].scrollIntoView({{ behavior: 'smooth', block: 'center' }}); l[i].style.backgroundColor = 'yellow';}}}}"
    subprocess.run(f'chrome-cli execute "{findLink}"', shell=True)
    
    # Every 0.2 seconds, check if page title has changed to the target
    while currentPage != activePath[i]:
        time.sleep(0.2)
        currentPage = getWikiTitle()
print('Done! 😎')