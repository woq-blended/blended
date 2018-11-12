<#include "header.ftl">
<#include "menu.ftl">

<div class="col-md-2 bg-light">
</div>

<div class="col-md-8" id="page" style="overflow: hidden">
  <script>var pageBar = new PerfectScrollbar('#page');</script>

  <div class="container scrollbars">
  ${content.body}

<#include "footer.ftl">