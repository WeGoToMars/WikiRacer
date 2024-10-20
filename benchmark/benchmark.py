import subprocess
import requests
import json
import os
import time
import csv
from urllib.parse import unquote

def random_article():
    url = "https://en.wikipedia.org/wiki/Special:Random"
    response = requests.get(url, allow_redirects=False)
    if response.status_code == 302:
        location = response.headers['Location']
        decoded_title = unquote(location.replace("https://en.wikipedia.org/wiki/", ""))
        return decoded_title
    else:
        raise Exception("Failed to retrieve the random article")
    
def run_benchmark(START, FINISH):
    print(f"Wikipedia search from {START} to {FINISH}")

    result = bytes.decode(subprocess.run(['java', '-jar', 'wikiracer.jar', '--start', START, '--finish', FINISH, '-q', '-b'], stdout=subprocess.PIPE).stdout)

    racer_execution_time = float(result.split('|')[3])
    racer_path_length = int(result.split('|')[4])

    start_time = time.time()

    # Request paths from the 6-degrees-of-wikipedia API
    # https://www.sixdegreesofwikipedia.com/
    # https://github.com/jwngr/sdow
    headers = {'Content-Type': 'application/json'}
    json_data = {'source': START, 'target': FINISH}
    start_time = time.time()
    response = json.loads(requests.post('https://api.sixdegreesofwikipedia.com/paths', headers=headers, json=json_data).text)

    six_degrees_execution_time = round(time.time() - start_time, 3)
    
    if ('error' in response.keys()):
        print(response)
        six_degrees_path_length = 0
        six_degrees_paths_num = 0
    else:
        # Process paths into lists of URLs
        paths = []
        for path in response['paths']:
            paths.append([response['pages'][str(page)]['url'] for page in path[1:]])

        six_degrees_path_length = len(paths[0]) + 1
        six_degrees_paths_num = len(paths)

    print(f"Racer: {racer_path_length} steps in {racer_execution_time} seconds")
    print(f"Six Degrees: {six_degrees_path_length} steps in {six_degrees_execution_time} seconds with {six_degrees_paths_num} alternative paths")

    return racer_path_length, racer_execution_time, six_degrees_path_length, six_degrees_execution_time, six_degrees_paths_num

if __name__ == "__main__":
    N = 1000

    with open('benchmark.csv', 'w', newline='') as csvfile:
        writer = csv.writer(csvfile, delimiter=',')
        writer.writerow(['start_page', 'finish_page', 'racer_path_length', 'racer_execution_time', 'six_degrees_path_length', 'six_degrees_execution_time', 'six_degrees_paths_num'])

        for i in range(N):
            try:
                print(f"Running benchmark {i+1}/{N}")
                START, FINISH = random_article(), random_article()
                racer_path_length, racer_execution_time, six_degrees_path_length, six_degrees_execution_time, six_degrees_paths_num = run_benchmark(START, FINISH)
                writer.writerow([START, FINISH, racer_path_length, racer_execution_time, six_degrees_path_length, six_degrees_execution_time, six_degrees_paths_num])
            except Exception as e:
                print(e)
            csvfile.flush()