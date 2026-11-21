// Narrow screens: the menu button drops the section links down under the header.
(function () {
  var nav = document.querySelector('.nav'), btn = nav && nav.querySelector('.menu');
  if (!btn) return;
  nav.classList.add('has-menu');
  function set(open) { nav.classList.toggle('open', open); btn.setAttribute('aria-expanded', open); }
  btn.addEventListener('click', function () { set(!nav.classList.contains('open')); });
  nav.querySelectorAll('.links a').forEach(function (a) { a.addEventListener('click', function () { set(false); }); });
  document.addEventListener('click', function (e) { if (!nav.contains(e.target)) set(false); });
  document.addEventListener('keydown', function (e) { if (e.key === 'Escape' && nav.classList.contains('open')) { set(false); btn.focus(); } });
})();
