/* ==========================================================================
   SPO:IT Docs — progressive enhancements
   No dependencies, no network requests. The article remains readable without JS.
   ========================================================================== */

(function () {
  "use strict";

  document.documentElement.classList.add("js");

  var header = document.querySelector(".site-header");
  var progressBar = document.getElementById("reading-progress-bar");
  var sections = Array.prototype.slice.call(document.querySelectorAll(".doc-section"));
  var featureChapters = Array.prototype.slice.call(document.querySelectorAll(".feature-chapter"));
  var sectionLinks = Array.prototype.slice.call(document.querySelectorAll("[data-section-link]:not([data-feature-link])"));
  var featureLinks = Array.prototype.slice.call(document.querySelectorAll("[data-feature-link]"));
  var mobileCurrent = document.querySelector("[data-current-section]");
  var mobileNavBar = document.querySelector(".mobile-nav-wrap");
  var copyLive = document.getElementById("copy-live");

  /* ------------------------------------------------------------------------
     Search — filters an in-memory index, never hides the article.
     ------------------------------------------------------------------------ */

  var searchTrigger = document.getElementById("search-trigger");
  var searchPanel = document.getElementById("doc-search");
  var searchInput = document.getElementById("doc-search-input");
  var searchClose = document.getElementById("search-close");
  var searchResults = document.getElementById("search-results");
  var searchCount = document.getElementById("search-count");
  var searchOpen = false;
  var searchIndex = [];

  function normalize(value) {
    return String(value || "")
      .replace(/\s+/g, " ")
      .trim()
      .toLowerCase();
  }

  if (sections.length) {
    var sectionEntries = sections.map(function (section) {
      var heading = section.querySelector("h2");
      var headingText = heading ? heading.querySelector(".heading-text") : null;
      var kicker = section.querySelector(".section-kicker");
      var subheads = Array.prototype.slice
        .call(section.querySelectorAll("h3 .heading-text"))
        .filter(function (node) {
          return !node.closest(".feature-chapter");
        })
        .map(function (node) {
          return node.textContent.trim();
        });

      var title = headingText ? headingText.textContent.trim() : section.id;
      var searchRoot = section.cloneNode(true);
      var nestedChapters = searchRoot.querySelector(".feature-chapters");
      var featureMap = searchRoot.querySelector(".feature-map");

      /* The hub and its 08.1–08.8 chapters are separate results. */
      if (nestedChapters) {
        nestedChapters.remove();
      }
      if (featureMap) {
        featureMap.remove();
      }

      var sectionText = searchRoot.textContent || "";

      return {
        id: section.id,
        kind: "section",
        title: title,
        number: section.getAttribute("data-number") || "",
        kicker: kicker ? kicker.textContent.trim() : "",
        subheads: subheads,
        haystack: normalize(
          [title, subheads.join(" "), section.getAttribute("data-search") || "", kicker ? kicker.textContent : "", sectionText].join(" ")
        )
      };
    });

    var featureEntries = featureChapters.map(function (chapter) {
      var headingText = chapter.querySelector(".feature-title h3 .heading-text");
      var kicker = chapter.querySelector(".feature-kicker");
      var subheads = Array.prototype.slice
        .call(chapter.querySelectorAll("h4"))
        .map(function (node) {
          var copy = node.cloneNode(true);
          Array.prototype.slice.call(copy.querySelectorAll(".source-tag")).forEach(function (tag) {
            tag.remove();
          });
          return copy.textContent.replace(/\s+/g, " ").trim();
        });
      var title = headingText ? headingText.textContent.trim() : chapter.id;
      var chapterText = chapter.innerText || chapter.textContent || "";

      return {
        id: chapter.id,
        kind: "feature",
        title: title,
        number: chapter.getAttribute("data-number") || "",
        kicker: kicker ? kicker.textContent.trim() : "",
        subheads: subheads,
        haystack: normalize(
          [title, subheads.join(" "), chapter.getAttribute("data-search") || "", kicker ? kicker.textContent : "", chapterText].join(" ")
        )
      };
    });

    searchIndex = sectionEntries.concat(featureEntries);
  }

  function appendHighlighted(element, text, query) {
    if (!query) {
      element.textContent = text;
      return;
    }

    var lowerText = text.toLowerCase();
    var at = lowerText.indexOf(query);

    if (at < 0) {
      element.textContent = text;
      return;
    }

    element.appendChild(document.createTextNode(text.slice(0, at)));
    var mark = document.createElement("mark");
    mark.textContent = text.slice(at, at + query.length);
    element.appendChild(mark);
    element.appendChild(document.createTextNode(text.slice(at + query.length)));
  }

  function searchScore(item, query) {
    var score = 0;
    var title = normalize(item.title);

    if (title.indexOf(query) !== -1) {
      score += item.kind === "feature" ? 120 : 100;
    }

    for (var i = 0; i < item.subheads.length; i += 1) {
      if (normalize(item.subheads[i]).indexOf(query) !== -1) {
        score += 40;
        break;
      }
    }

    if (item.haystack.indexOf(query) !== -1) {
      score += 10;
    }

    return score + (item.kind === "feature" ? 2 : 0);
  }

  function renderSearch(rawQuery) {
    if (!searchResults) {
      return;
    }

    var query = normalize(rawQuery);
    var matches = query
      ? searchIndex.filter(function (item) {
          return item.haystack.indexOf(query) !== -1;
        })
      : searchIndex;

    if (query) {
      matches = matches.slice().sort(function (a, b) {
        var difference = searchScore(b, query) - searchScore(a, query);
        return difference !== 0 ? difference : searchIndex.indexOf(a) - searchIndex.indexOf(b);
      });
    }

    searchResults.textContent = "";

    if (!matches.length) {
      var empty = document.createElement("li");
      empty.className = "search-empty";
      var emptyTitle = document.createElement("strong");
      emptyTitle.textContent = "일치하는 항목이 없습니다.";
      var emptyBody = document.createElement("span");
      emptyBody.textContent = "기능 이름이나 짧은 경로로 다시 검색해 보세요. 예: 매치, 로그인, club_join_request, /mypage";
      empty.appendChild(emptyTitle);
      empty.appendChild(emptyBody);
      searchResults.appendChild(empty);
    } else {
      matches.forEach(function (item) {
        var listItem = document.createElement("li");
        listItem.className = "search-result";

        var link = document.createElement("a");
        link.href = "#" + item.id;

        var number = document.createElement("span");
        number.className = "result-num";
        number.textContent = item.number;

        var title = document.createElement("span");
        title.className = "result-title";
        appendHighlighted(title, item.title, query);

        var context = document.createElement("span");
        context.className = "result-context";

        var matchedSubhead = "";
        if (query) {
          for (var i = 0; i < item.subheads.length; i += 1) {
            if (item.subheads[i].toLowerCase().indexOf(query) !== -1) {
              matchedSubhead = item.subheads[i];
              break;
            }
          }
        }

        if (matchedSubhead) {
          context.textContent = (item.kind === "feature" ? "기능 소제목 · " : "소제목 · ") + matchedSubhead;
        } else if (item.kind === "feature") {
          context.textContent = (item.kicker ? item.kicker + " · " : "") + "기능 " + item.number;
        } else {
          context.textContent = (item.kicker ? item.kicker + " · " : "") + "장 " + item.number;
        }

        link.appendChild(number);
        link.appendChild(title);
        link.appendChild(context);
        listItem.appendChild(link);
        searchResults.appendChild(listItem);
      });
    }

    if (searchCount) {
      searchCount.textContent = query
        ? matches.length + "개 결과"
        : "전체 11개 장 · 8개 기능";
    }
  }

  function openSearch() {
    if (!searchPanel || !searchInput || !searchTrigger) {
      return;
    }

    searchPanel.hidden = false;
    searchOpen = true;
    searchTrigger.setAttribute("aria-expanded", "true");
    renderSearch(searchInput.value);
    searchInput.focus();
    searchInput.select();
  }

  function closeSearch(returnFocus) {
    if (!searchPanel || !searchTrigger) {
      return;
    }

    searchPanel.hidden = true;
    searchOpen = false;
    searchTrigger.setAttribute("aria-expanded", "false");

    if (returnFocus) {
      searchTrigger.focus();
    }
  }

  if (searchTrigger && searchPanel && searchInput) {
    searchTrigger.hidden = false;

    searchTrigger.addEventListener("click", function () {
      if (searchOpen) {
        closeSearch(true);
      } else {
        openSearch();
      }
    });

    searchInput.addEventListener("input", function () {
      renderSearch(searchInput.value);
    });

    searchInput.addEventListener("keydown", function (event) {
      if (event.isComposing || event.keyCode === 229) {
        return;
      }

      if (event.key === "Enter") {
        var firstResult = searchResults ? searchResults.querySelector("a") : null;
        if (firstResult) {
          event.preventDefault();
          firstResult.click();
        }
      } else if (event.key === "ArrowDown" && searchResults) {
        var firstLink = searchResults.querySelector("a");
        if (firstLink) {
          event.preventDefault();
          firstLink.focus();
        }
      }
    });

    if (searchResults) {
      searchResults.addEventListener("click", function (event) {
        var link = event.target.closest ? event.target.closest("a") : null;
        if (link) {
          closeSearch(false);
        }
      });

      searchResults.addEventListener("keydown", function (event) {
        if (event.key !== "ArrowDown" && event.key !== "ArrowUp") {
          return;
        }

        var links = Array.prototype.slice.call(searchResults.querySelectorAll("a"));
        var current = links.indexOf(document.activeElement);

        if (event.key === "ArrowDown") {
          event.preventDefault();
          if (links.length) {
            links[current + 1 < links.length ? current + 1 : 0].focus();
          }
        } else {
          event.preventDefault();
          if (current <= 0) {
            searchInput.focus();
          } else {
            links[current - 1].focus();
          }
        }
      });
    }

    if (searchClose) {
      searchClose.addEventListener("click", function () {
        closeSearch(true);
      });
    }

    document.addEventListener("pointerdown", function (event) {
      if (!searchOpen) {
        return;
      }

      if (searchPanel.contains(event.target) || searchTrigger.contains(event.target)) {
        return;
      }

      closeSearch(false);
    });

    document.addEventListener("keydown", function (event) {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "k") {
        event.preventDefault();
        if (searchOpen) {
          closeSearch(true);
        } else {
          openSearch();
        }
        return;
      }

      if (event.key === "Escape" && searchOpen) {
        event.preventDefault();
        closeSearch(true);
      }
    });
  }

  /* ------------------------------------------------------------------------
     Active section + reading progress
     ------------------------------------------------------------------------ */

  var activeKey = "";

  function setActive(sectionId, featureId) {
    var nextKey = sectionId + "|" + (featureId || "");

    if (!sectionId || nextKey === activeKey) {
      return;
    }

    activeKey = nextKey;

    sectionLinks.forEach(function (link) {
      var target = (link.getAttribute("href") || "").replace(/^#/, "");
      if (target === sectionId) {
        link.setAttribute("aria-current", "location");
      } else {
        link.removeAttribute("aria-current");
      }
    });

    featureLinks.forEach(function (link) {
      var target = (link.getAttribute("href") || "").replace(/^#/, "");
      if (featureId && target === featureId) {
        link.setAttribute("aria-current", "location");
      } else {
        link.removeAttribute("aria-current");
      }
    });

    if (mobileCurrent) {
      var unit = document.getElementById(featureId || sectionId);
      if (unit) {
        var number = unit.getAttribute("data-number") || "";
        var titleNode = featureId
          ? unit.querySelector(".feature-title h3 .heading-text")
          : unit.querySelector("h2 .heading-text");
        var title = titleNode ? titleNode.textContent.trim() : "";
        mobileCurrent.textContent = number ? number + " · " + title : title;
      }
    }
  }

  function updateScrollUI() {
    var root = document.documentElement;
    var scrollTop = window.pageYOffset || root.scrollTop || 0;
    var scrollHeight = root.scrollHeight;
    var viewportHeight = window.innerHeight;
    var maxScroll = scrollHeight - viewportHeight;
    var ratio = maxScroll > 0 ? Math.min(1, Math.max(0, scrollTop / maxScroll)) : 0;

    if (progressBar) {
      progressBar.style.transform = "scaleX(" + ratio + ")";
    }

    if (!sections.length) {
      return;
    }

    var offset = header ? header.offsetHeight : 64;

    if (mobileNavBar && window.getComputedStyle(mobileNavBar).display !== "none") {
      offset += mobileNavBar.offsetHeight + 24;
    } else {
      offset += 72;
    }

    var current = sections[0];

    sections.forEach(function (section) {
      if (section.getBoundingClientRect().top <= offset) {
        current = section;
      }
    });

    if (scrollTop + viewportHeight >= scrollHeight - 4) {
      current = sections[sections.length - 1];
    }

    var activeFeature = null;

    if (current.id === "features") {
      featureChapters.forEach(function (chapter) {
        if (chapter.getBoundingClientRect().top <= offset + 8) {
          activeFeature = chapter;
        }
      });
    }

    setActive(current.id, activeFeature ? activeFeature.id : "");
  }

  var scrollScheduled = false;

  function scheduleScrollUI() {
    if (scrollScheduled) {
      return;
    }

    scrollScheduled = true;
    window.requestAnimationFrame(function () {
      updateScrollUI();
      scrollScheduled = false;
    });
  }

  window.addEventListener("scroll", scheduleScrollUI, { passive: true });
  window.addEventListener("resize", scheduleScrollUI);
  window.addEventListener("hashchange", scheduleScrollUI);
  window.addEventListener("load", scheduleScrollUI);

  updateScrollUI();
  window.setTimeout(scheduleScrollUI, 120);

  /* ------------------------------------------------------------------------
     Mobile section navigation
     ------------------------------------------------------------------------ */

  var mobileNav = document.querySelector(".mobile-nav");

  if (mobileNav) {
    mobileNav.addEventListener("click", function (event) {
      var link = event.target.closest ? event.target.closest("a") : null;
      if (link) {
        mobileNav.open = false;
      }
    });

    var desktopQuery = window.matchMedia("(min-width: 1001px)");
    var closeMobileNav = function (event) {
      if (event.matches) {
        mobileNav.open = false;
      }
    };

    if (typeof desktopQuery.addEventListener === "function") {
      desktopQuery.addEventListener("change", closeMobileNav);
    } else if (typeof desktopQuery.addListener === "function") {
      desktopQuery.addListener(closeMobileNav);
    }
  }

  /* ------------------------------------------------------------------------
     Checklist — local, non-persisted progress
     ------------------------------------------------------------------------ */

  var checklist = document.querySelector("[data-checklist]");

  if (checklist) {
    var boxes = Array.prototype.slice.call(checklist.querySelectorAll('input[type="checkbox"]'));
    var checkCount = checklist.querySelector("[data-check-count]");
    var checkProgress = checklist.querySelector(".check-progress");
    var checkBar = checklist.querySelector("[data-check-bar]");

    var updateChecklist = function () {
      var checked = boxes.filter(function (box) {
        return box.checked;
      }).length;

      if (checkCount) {
        checkCount.hidden = false;
        checkCount.textContent = checked + " / " + boxes.length;
      }

      if (checkProgress) {
        checkProgress.hidden = false;
        checkProgress.setAttribute("aria-valuenow", String(checked));
        checkProgress.setAttribute("aria-valuemax", String(boxes.length));
      }

      if (checkBar) {
        checkBar.style.width = boxes.length ? (checked / boxes.length) * 100 + "%" : "0%";
      }
    };

    boxes.forEach(function (box) {
      box.addEventListener("change", updateChecklist);
    });

    updateChecklist();
  }

  /* ------------------------------------------------------------------------
     Code copy — Clipboard API, execCommand fallback, manual selection
     ------------------------------------------------------------------------ */

  function announceCopy(message) {
    if (!copyLive) {
      return;
    }
    copyLive.textContent = "";
    window.setTimeout(function () {
      copyLive.textContent = message;
    }, 30);
  }

  function selectCode(pre) {
    var selection = window.getSelection ? window.getSelection() : null;
    if (!selection) {
      return;
    }

    var range = document.createRange();
    range.selectNodeContents(pre);
    selection.removeAllRanges();
    selection.addRange(range);

    try {
      pre.focus({ preventScroll: true });
    } catch (error) {
      pre.focus();
    }
  }

  async function writeClipboard(text) {
    if (navigator.clipboard && window.isSecureContext) {
      try {
        await navigator.clipboard.writeText(text);
        return true;
      } catch (error) {
        /* Permission or browser policy can reject even when the API exists. */
      }
    }

    var textarea = document.createElement("textarea");
    textarea.value = text;
    textarea.setAttribute("readonly", "");
    textarea.style.position = "fixed";
    textarea.style.top = "-9999px";
    textarea.style.left = "-9999px";
    textarea.style.opacity = "0";
    document.body.appendChild(textarea);
    textarea.select();
    textarea.setSelectionRange(0, textarea.value.length);

    var copied = false;
    try {
      copied = document.execCommand("copy");
    } catch (error) {
      copied = false;
    }

    document.body.removeChild(textarea);
    return copied;
  }

  Array.prototype.slice.call(document.querySelectorAll(".code-block")).forEach(function (block) {
    var pre = block.querySelector("pre");
    var slot = block.querySelector(".copy-slot");
    var languageNode = block.querySelector(".code-lang");

    if (!pre || !slot) {
      return;
    }

    var language = languageNode ? languageNode.textContent.trim() : "코드";
    var button = document.createElement("button");
    var status = document.createElement("span");

    button.type = "button";
    button.className = "copy-btn";
    button.textContent = "복사";
    button.setAttribute("aria-label", language + " 코드 복사");

    status.className = "copy-status";
    status.setAttribute("data-copy-status", "");

    slot.appendChild(button);
    slot.appendChild(status);

    var resetTimer = null;

    button.addEventListener("click", async function () {
      if (resetTimer) {
        window.clearTimeout(resetTimer);
      }

      button.disabled = true;
      var copied = await writeClipboard(pre.textContent);
      button.disabled = false;

      if (copied) {
        button.textContent = "복사됨";
        button.setAttribute("data-copied", "true");
        status.textContent = "복사됨";
        announceCopy(language + " 코드를 클립보드에 복사했습니다.");
      } else {
        selectCode(pre);
        button.textContent = "선택됨";
        button.removeAttribute("data-copied");
        status.textContent = "복사할 수 없습니다. 코드를 직접 선택해 주세요.";
        announceCopy("자동 복사에 실패했습니다. 코드를 선택했으니 직접 복사해 주세요.");
      }

      resetTimer = window.setTimeout(function () {
        button.textContent = "복사";
        button.removeAttribute("data-copied");
        if (copied && status.textContent === "복사됨") {
          status.textContent = "";
        }
      }, 2600);
    });
  });

  /* Allow horizontal keyboard scrolling of code when it actually overflows. */
  function updateCodeFocusability() {
    Array.prototype.slice.call(document.querySelectorAll(".code-block pre")).forEach(function (pre) {
      if (pre.scrollWidth > pre.clientWidth + 2) {
        pre.setAttribute("tabindex", "0");
        if (!pre.hasAttribute("aria-label")) {
          pre.setAttribute("aria-label", "가로로 스크롤할 수 있는 코드 예시");
        }
      } else if (pre.getAttribute("data-auto-focus") === "true") {
        pre.removeAttribute("tabindex");
        pre.removeAttribute("aria-label");
        pre.removeAttribute("data-auto-focus");
      }

      if (pre.scrollWidth > pre.clientWidth + 2) {
        pre.setAttribute("data-auto-focus", "true");
      }
    });
  }

  var codeResizeScheduled = false;
  function scheduleCodeFocusability() {
    if (codeResizeScheduled) {
      return;
    }
    codeResizeScheduled = true;
    window.requestAnimationFrame(function () {
      updateCodeFocusability();
      codeResizeScheduled = false;
    });
  }

  window.addEventListener("resize", scheduleCodeFocusability);
  window.setTimeout(updateCodeFocusability, 80);
  window.setTimeout(updateCodeFocusability, 500);
})();
