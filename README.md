# Wikiracer 🏁

WikiRacer is a Java program that finds a hyperlink path between two pages in the English Wikipedia, blazingly fast!

<sub>_Originally built as a school project in February 2024. Uploaded to GitHub with code cleanup and aditional performance improvements in October 2024._<sub>

Use it for exploration, or to troll your friends by cheating at an online [wiki-race](https://wiki-race.com/).

> https://en.wikipedia.org/wiki/Wikipedia:Wiki_Game: <br>
[Wikirace] is a race between any number of participants, using wikilinks to travel from one Wikipedia page to another. The first person to reach the destination page, or the person that reaches the destination using the fewest links, wins the race.

WikiRacer uses only runtime calls to Wikimedia API, to [links](https://www.mediawiki.org/wiki/API:Links) and [linkshere](https://www.mediawiki.org/wiki/API:Linkshere) endpoints with no cached data.

## Usage

```
java -jar wikiracer.jar
```
Optional parameters:
```
--start [page title] // specify starting page
--finish [page title] // specify ending page
-q // quiet mode, produces less output log
-b // benchmark mode, produces an extra pipe delimited string to save to csv
-i // interactive mode, uses chrome-cli to guide the user through the found path
```

## Interactive mode (chrome-cli)

> [!IMPORTANT]
> This mode only works on MacOS with Chrome browser and [chrome-cli](https://github.com/prasmussen/chrome-cli) installed.

This mode guides you on the found path by scrolling to and highlighting links in your browser. Verified to work with [en.wikipedia.org](https://en.wikipedia.org/) and [wiki-race.com](https://wiki-race.com/)

1. Start `wikiracer.jar` with the `-i` parameter while the Wikipedia page is currently open in the background.
2. Input the end page in the console.
3. Wait for the path to be found and click the highlighted links.
4. Enjoy!

## Benchmarks

WikiRacer was benchmarked on 350 pairs of random Wikipedia articles. See [benchmark.csv](benchmark/benchmark.csv) for its log.

It achieved a total run time of **1.933** seconds (median) or **2.033** seconds (average).

The average path length was only **5.90** articles (including the starting one), meaning the median race would take only **5 hyperlink clicks** to finish.

<p align="center">
<img height="250" alt="image" src="https://github.com/user-attachments/assets/0cd985ee-a4b0-4ade-8040-6ddc93e59a2a">
<img height="250" alt="image" src="https://github.com/user-attachments/assets/a67f1439-f641-4175-94cc-8b125eb34376">
</p>

Comparatively, Six Degrees of Wikipedia achieves a runtime of **2.532** seconds (median) or **3.992** seconds (average) with an average path length of **5.157** articles. See below for more details on project.

<p align="center">
<img height="350" alt="image" src="https://github.com/user-attachments/assets/623995ea-0d05-45cc-8b40-859b64d6a356">
</p>

## Notes about Six Degrees of Wikipedia
[Six Degrees of Wikipedia](https://www.sixdegreesofwikipedia.com/) project uses a [different approach](https://github.com/jwngr/sdow/blob/master/docs/data-source.md), searching through the graph of 500M+ links released by Wikimedia in SQL dumps. As I'm writing this, the [latest](https://dumps.wikimedia.org/enwiki/latest/) *pagelinks* dump is 6.78 GB in size, *page* is 2.25 GB, and *redirects* is 170 MB.

It represents the "proper" approach, finding all paths of minimal length simultaneously. Therefore, it was chosen as a target for the benchmark as I was wondering how close to its length I could come without using stored data. 

However, that uses up a big chunk of RAM and requires ~10 GB of data has to be continuously re-downloaded and rebuilt to keep up with Wikipedia changes, which a a significant limitation.
