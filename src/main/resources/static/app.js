(() => {
  "use strict";
  const $ = (id) => document.getElementById(id);
  const STORAGE_KEY = "urjaroute.connection.v1";
  const DEMO = {
    vehicles: [
      {id:"EV-042",currentSoc:.82,batteryCapacityKwh:50,efficiencyKwhPerKm:.18,maxChargeRateKw:60,currentLocationId:"BELLANDUR",availableFrom:new Date().toISOString()},
      {id:"EV-018",currentSoc:.64,batteryCapacityKwh:50,efficiencyKwhPerKm:.19,maxChargeRateKw:50,currentLocationId:"E_CITY",availableFrom:new Date().toISOString()},
      {id:"EV-031",currentSoc:.47,batteryCapacityKwh:60,efficiencyKwhPerKm:.17,maxChargeRateKw:60,currentLocationId:"WHITEFIELD",availableFrom:new Date().toISOString()}
    ],
    trips: [
      {id:"TR-2084",pickupLocationId:"Indiranagar",dropoffLocationId:"Bellandur",pickupWindowStart:new Date(Date.now()+600000).toISOString(),pickupWindowEnd:new Date(Date.now()+1800000).toISOString(),deadline:new Date(Date.now()+3600000).toISOString(),distanceKm:8.4,passengerCount:1,status:"PENDING"},
      {id:"TR-2083",pickupLocationId:"HSR Layout",dropoffLocationId:"Electronic City",pickupWindowStart:new Date(Date.now()+300000).toISOString(),pickupWindowEnd:new Date(Date.now()+1500000).toISOString(),deadline:new Date(Date.now()+3300000).toISOString(),distanceKm:11.2,passengerCount:2,status:"PENDING"},
      {id:"TR-2082",pickupLocationId:"Whitefield",dropoffLocationId:"Marathahalli",pickupWindowStart:new Date(Date.now()).toISOString(),pickupWindowEnd:new Date(Date.now()+1200000).toISOString(),deadline:new Date(Date.now()+3000000).toISOString(),distanceKm:7.6,passengerCount:1,status:"PENDING"},
      {id:"TR-2081",pickupLocationId:"Koramangala",dropoffLocationId:"Bellandur",pickupWindowStart:new Date(Date.now()-300000).toISOString(),pickupWindowEnd:new Date(Date.now()+900000).toISOString(),deadline:new Date(Date.now()+2700000).toISOString(),distanceKm:6.1,passengerCount:1,status:"PENDING"}
    ]
  };
  let connection = loadConnection();
  let state = {connected:false,vehicles:[],trips:[],hubs:[],versions:[],proposal:null,version:0};

  function loadConnection() {
    try { return {...{base:location.origin,key:"key-admin-demo-tenant"},...JSON.parse(localStorage.getItem(STORAGE_KEY)||"{}")}; }
    catch { return {base:location.origin,key:"key-admin-demo-tenant"}; }
  }
  function saveConnection() {
    connection = {base:($("api-base-input").value||location.origin).replace(/\/+$/,""),key:$("api-key-input").value.trim()};
    localStorage.setItem(STORAGE_KEY,JSON.stringify(connection));
  }
  function toast(message, error=false) {
    const node=document.createElement("div"); node.className="toast"+(error?" error":""); node.textContent=message; $("toast-region").appendChild(node);
    setTimeout(()=>node.remove(),4300);
  }
  async function api(path, options={}) {
    const headers={"Content-Type":"application/json","X-API-KEY":connection.key,...(options.headers||{})};
    const response=await fetch(connection.base+path,{...options,headers});
    const raw=await response.text(); let data=null;
    try { data=raw?JSON.parse(raw):null; } catch { data=raw; }
    if(!response.ok) throw new Error(data?.detail||data?.message||`Request failed (${response.status})`);
    return data;
  }
  function setConnected(connected) {
    state.connected=connected;
    $("connection-label").textContent=connected?"API connected · live data":"Demo mode · click to connect";
    $("connection-button").querySelector(".connection-light").classList.toggle("connected",connected);
    $("system-status-text").textContent=connected?"API CONNECTED":"DEMO MODE";
    $("system-status-text").parentElement.querySelector("i").style.background=connected?"#27b57b":"#d9a24b";
  }
  function fmtTime(value) {
    if(!value)return "—";
    return new Intl.DateTimeFormat("en-IN",{hour:"2-digit",minute:"2-digit",hour12:false,timeZone:"Asia/Kolkata"}).format(new Date(value));
  }
  function esc(value) { return String(value??"").replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c])); }
  function setDemoState() {
    state.vehicles=DEMO.vehicles; state.trips=DEMO.trips; state.hubs=[];
    renderFleet(); renderTrips();
    $("metric-vehicles").innerHTML='24<span class="metric-unit">/ 30</span>';
    $("vehicle-subtext").textContent="80% available";
    $("metric-coverage").innerHTML='94.2<span class="metric-unit">%</span>';
    $("metric-cost").textContent="₹ 8,460";
    $("trip-total").textContent="1,248"; $("trip-covered-label").textContent="94.2% covered";
    $("coverage-bar").style.width="94.2%"; $("ev-assigned-count").textContent="1,176"; $("backup-count").textContent="72"; $("pending-count").textContent="0";
    $("trip-table-count").textContent="Showing 4 of 1,248 trips";
    $("nav-vehicle-count").textContent="30";
  }
  async function refresh() {
    $("refresh-button").style.transform="rotate(180deg)";
    try {
      const [vehicles,trips,hubs,overview,versions] = await Promise.all([
        api("/api/v1/vehicles"),api("/api/v1/trips"),api("/api/v1/hubs"),api("/api/v1/overview"),api("/api/v1/plans/versions")
      ]);
      state.vehicles=Array.isArray(vehicles)?vehicles:[]; state.trips=Array.isArray(trips)?trips:[]; state.hubs=Array.isArray(hubs)?hubs:[];
      state.versions=Array.isArray(versions)?versions:[];
      renderFleet(); renderTrips(); renderHubs();
      const v=overview||{};
      $("metric-vehicles").innerHTML=`${Number(v.vehicleCount??state.vehicles.length)}<span class="metric-unit"> registered</span>`;
      $("vehicle-subtext").textContent="Tenant fleet";
      const serviceRate=Math.max(0,Math.min(1,Number(v.serviceRate??0)));
      $("metric-coverage").innerHTML=`${(serviceRate*100).toFixed(1)}<span class="metric-unit">%</span>`;
      $("coverage-subtext").textContent=state.versions.length?"accepted plan coverage":"no accepted plan yet";
      $("trip-total").textContent=Number(v.tripCount??state.trips.length).toLocaleString("en-IN");
      $("trip-covered-label").textContent=state.versions.length?`${(serviceRate*100).toFixed(1)}% covered`:"No accepted plan";
      $("metric-cost").textContent="₹ "+Number(v.latestPlanCost??0).toLocaleString("en-IN",{maximumFractionDigits:0});
      $("ev-assigned-count").textContent=Number(v.evAssignedTripCount??0).toLocaleString("en-IN");
      $("backup-count").textContent=Number(v.backupTripCount??0).toLocaleString("en-IN");
      $("pending-count").textContent=Math.max(0,Number(v.tripCount??state.trips.length)-Number(v.evAssignedTripCount??0)-Number(v.backupTripCount??0));
      $("coverage-bar").style.width=`${serviceRate*100}%`;
      $("nav-vehicle-count").textContent=state.vehicles.length;
      state.version=Number(v.currentVersion??0); $("plan-version-pill").textContent=`VERSION ${state.version}`;
      setConnected(true);
      $("last-sync").textContent="Synced "+new Date().toLocaleTimeString("en-IN",{hour:"2-digit",minute:"2-digit"});
      $("hero-updated").textContent="Live backend data · "+state.vehicles.length+" vehicles registered";
      toast("Workspace data refreshed.");
    } catch(err) {
      setConnected(false); setDemoState();
      $("last-sync").textContent="Demo snapshot · API unavailable";
      $("hero-updated").textContent="Demo operations view · connect API for live data";
      if(err.message && !/Failed to fetch/i.test(err.message)) console.info("UrjaRoute API:",err.message);
    } finally { setTimeout(()=>$("refresh-button").style.transform="",180); }
  }
  function renderFleet() {
    if(!state.vehicles.length) {
      $("fleet-list").innerHTML='<div class="empty-state">No electric vehicles registered for this tenant. Click "+ Register EV" to add one.</div>';
      return;
    }
    $("fleet-list").innerHTML=state.vehicles.slice(0,5).map(v=>{
      const soc=Math.max(0,Math.min(100,Number(v.currentSoc??v.soc??0)*100));
      const capacity=Number(v.batteryCapacityKwh??50);
      const stateLabel=soc<20?"LOW RESERVE":"READY";
      return `<div class="fleet-row"><div class="vehicle-icon">EV</div><div class="fleet-info"><b>${esc(v.id)} <span class="vehicle-state ${soc<20?"charging":"ready"}">${stateLabel}</span></b><small>${esc(v.currentLocationId||"Fleet base")} · ${soc<20?"Recharge required":"Registered vehicle"}</small></div><div class="battery"><div><span>${soc.toFixed(0)}% SoC</span><small>${(capacity*soc/100).toFixed(1)} / ${capacity} kWh</small></div><div class="battery-track"><i style="width:${soc}%;${soc<20?"background:#dca14b":""}"></i></div></div></div>`;
    }).join("");
    $("metric-reserve").innerHTML='15<span class="metric-unit">%</span>';
  }
  function renderTrips() {
    if(!state.trips.length) {
      $("trips-table").innerHTML='<tr><td colspan="5" class="empty-state">No trips recorded for this tenant. Click "+ Register Trip" to dispatch one.</td></tr>';
      $("trip-table-count").textContent="Showing 0 of 0 trips";
      return;
    }
    const query=($("trip-search").value||"").toLowerCase();
    const filtered=state.trips.filter(t=>[t.id,t.pickupLocationId,t.dropoffLocationId,t.status].join(" ").toLowerCase().includes(query));
    $("trips-table").innerHTML=filtered.slice(0,50).map(t=>{
      const status=(t.status||"PENDING").toUpperCase(), backup=status.includes("BACKUP"), pending=status.includes("PENDING");
      const statusClass=backup?"backup":pending?"pending":"assigned";
      return `<tr><td class="mono">${esc(t.id)}</td><td><b>${esc(t.pickupLocationId)}</b><span class="route-arrow">→</span>${esc(t.dropoffLocationId)}</td><td>${fmtTime(t.pickupWindowStart)} – ${fmtTime(t.pickupWindowEnd)}</td><td>${Number(t.distanceKm||0).toFixed(1)} km</td><td><span class="table-status ${statusClass}">${esc(status.replaceAll("_"," "))}</span></td></tr>`;
    }).join("")||'<tr><td colspan="5">No trips match your search.</td></tr>';
    $("trip-table-count").textContent=`Showing ${Math.min(filtered.length,50)} of ${filtered.length} trips`;
  }
  function renderHubs() {
    if(!state.hubs.length) {
      $("hub-list").innerHTML='<div class="empty-state">No charger hubs registered for this tenant.</div>';
      $("charger-available").textContent="0"; $("charger-available-stat").textContent="0";
      $("charger-charging").textContent="0"; $("charger-offline").textContent="0";
      return;
    }
    $("hub-list").innerHTML=state.hubs.slice(0,5).map(h=>`<div class="hub-row"><span class="hub-status good"></span><span class="hub-name"><b>${esc(h.name||h.id)}</b><small>${Number(h.totalChargers||0)} plugs · availability reported by operator</small></span><span class="hub-capacity">${Number(h.totalChargers||0)} plugs</span></div>`).join("");
    const count=state.hubs.reduce((n,h)=>n+Number(h.totalChargers||0),0);
    $("charger-available").textContent=count; $("charger-available-stat").textContent=count; $("charger-charging").textContent="0"; $("charger-offline").textContent="0";
    $("network-ring").style.background="conic-gradient(#24b87c 0 100%,#e4ece8 100% 100%)";
  }

  async function generatePlan() {
    const buttons=[$("hero-plan-button"),$("quick-plan-button"),$("studio-plan-button")];
    buttons.forEach(b=>{b.disabled=true;b.dataset.old=b.textContent;b.textContent="Planning…";});
    const result=$("plan-result"); result.hidden=false; result.textContent="Building candidate plan and running independent safety checks…";
    try {
      const now=new Date(), end=new Date(now.getTime()+8*60*60*1000);
      const proposal=await api("/api/v1/plans/propose",{method:"POST",body:JSON.stringify({planningHorizonStart:now.toISOString(),planningHorizonEnd:end.toISOString(),vehicleIds:[],tripIds:[]})});
      state.proposal=proposal;
      const assignments=proposal.assignments||[];
      result.textContent=`PROPOSAL ${proposal.proposalId}\nValidation: ${proposal.valid?"PASS":"FAILED"}\nEV/backup assignments returned: ${assignments.length}\nEstimated charging cost: ₹${Number(proposal.totalCost||0).toFixed(2)}\nEV service rate: ${(Number(proposal.serviceRate||0)*100).toFixed(1)}%\n\n${proposal.valid?"Proposal is ready for acceptance.":"This proposal cannot be accepted until the validation failure is resolved."}`;
      document.querySelectorAll(".stage").forEach((s,i)=>s.classList.toggle("active",i<2));
      if(proposal.valid) {
        const accept=document.createElement("button"); accept.className="primary-button"; accept.textContent="Accept validated proposal →"; accept.style.marginTop="10px";
        accept.addEventListener("click",acceptPlan); result.appendChild(document.createElement("br")); result.appendChild(accept);
      }
      toast(proposal.valid?"Validated proposal created.":"Proposal created, but validation failed.",!proposal.valid);
    } catch(err) {
      result.textContent=`Could not generate a live plan.\n${err.message}\n\nRegister vehicles, trips and charger hubs first, then reconnect with an operator API key.`;
      toast("Plan generation failed: "+err.message,true);
    } finally { buttons.forEach(b=>{b.disabled=false;b.textContent=b.dataset.old||"Generate fleet plan";}); }
  }
  async function acceptPlan() {
    if(!state.proposal?.proposalId)return;
    try {
      const version=await api(`/api/v1/plans/${encodeURIComponent(state.proposal.proposalId)}/accept`,{method:"POST",headers:{"Idempotency-Key":crypto.randomUUID()},body:JSON.stringify({expectedCurrentVersion:state.version})});
      state.version=version.versionNumber; $("plan-version-pill").textContent=`VERSION ${state.version}`;
      document.querySelectorAll(".stage").forEach(s=>s.classList.add("active"));
      toast(`Plan version ${state.version} accepted and audited.`);
      await refresh();
    } catch(err){toast("Acceptance failed: "+err.message,true);}
  }
  async function simulateDrift() {
    const vehicle=state.vehicles[0];
    if(!state.connected||!vehicle){showRecovery("Demo event: battery drift detected on EV-042. A replacement plan would be proposed and independently validated. No live state was changed.");return;}
    try {
      const data=await api("/api/v1/replanning/battery-drift",{method:"POST",body:JSON.stringify({vehicleId:vehicle.id,observedSoc:Math.max(.16,Number(vehicle.currentSoc||.5)-.12),eventTime:new Date().toISOString()})});
      showRecovery(`BATTERY DRIFT RESPONSE\nOutcome: ${data.outcome}\nReplan time: ${data.replanDurationMs} ms\nValidation: ${data.valid?"PASS":"FAILED"}\nChanged assignments: ${(data.changedVehicleAssignments||[]).join(", ")||"none reported"}`);
    } catch(err){showRecovery("Replanning request failed: "+err.message,true);}
  }
  async function simulateFailure() {
    const hub=state.hubs[0];
    if(!state.connected||!hub){showRecovery("Demo event: charger outage detected at Bellandur Tech Park. The recovery flow preserves completed trips, repairs future charging slots and validates the replacement proposal.");return;}
    const start=new Date(), end=new Date(start.getTime()+60*60*1000);
    try {
      const data=await api("/api/v1/replanning/charger-failure",{method:"POST",body:JSON.stringify({hubId:hub.id,outageStart:start.toISOString(),estimatedRestoration:end.toISOString()})});
      showRecovery(`CHARGER OUTAGE RESPONSE\nOutcome: ${data.outcome}\nReplan time: ${data.replanDurationMs} ms\nValidation: ${data.valid?"PASS":"FAILED"}\nChanged assignments: ${(data.changedVehicleAssignments||[]).join(", ")||"none reported"}`);
    } catch(err){showRecovery("Replanning request failed: "+err.message,true);}
  }
  function showRecovery(message,error=false){const el=$("recovery-result");el.hidden=false;el.textContent=message;el.style.background=error?"#fff7f4":"#f4fff8";el.style.borderColor=error?"#f0d0c7":"#d6f0e1";toast(error?"Recovery event failed":"Recovery scenario evaluated",error);}

  async function registerVehicleSubmit(e) {
    e.preventDefault();
    const btn = $("submit-vehicle-btn");
    btn.disabled = true;
    try {
      const payload = {
        id: $("v-id").value.trim(),
        currentSoc: parseFloat($("v-soc").value),
        batteryCapacityKwh: parseFloat($("v-cap").value),
        efficiencyKwhPerKm: parseFloat($("v-eff").value),
        maxChargeRateKw: parseFloat($("v-rate").value),
        currentLocationId: $("v-loc").value.trim(),
        availableFrom: new Date().toISOString()
      };
      await api("/api/v1/vehicles", { method: "POST", body: JSON.stringify(payload) });
      toast(`Vehicle ${payload.id} registered.`);
      $("register-vehicle-dialog").close();
      $("register-vehicle-form").reset();
      await refresh();
    } catch(err) {
      toast("Vehicle registration failed: " + err.message, true);
    } finally {
      btn.disabled = false;
    }
  }

  async function registerTripSubmit(e) {
    e.preventDefault();
    const btn = $("submit-trip-btn");
    btn.disabled = true;
    try {
      const startMins = parseInt($("t-start-mins").value, 10);
      const endMins = parseInt($("t-end-mins").value, 10);
      const deadlineMins = parseInt($("t-deadline-mins").value, 10);
      const now = Date.now();

      const payload = {
        id: $("t-id").value.trim(),
        pickupLocationId: $("t-pickup").value.trim(),
        dropoffLocationId: $("t-dropoff").value.trim(),
        pickupWindowStart: new Date(now + startMins * 60000).toISOString(),
        pickupWindowEnd: new Date(now + endMins * 60000).toISOString(),
        deadline: new Date(now + deadlineMins * 60000).toISOString(),
        distanceKm: parseFloat($("t-dist").value),
        passengerCount: parseInt($("t-passengers").value, 10)
      };
      await api("/api/v1/trips", { method: "POST", body: JSON.stringify(payload) });
      toast(`Trip ${payload.id} scheduled.`);
      $("register-trip-dialog").close();
      $("register-trip-form").reset();
      await refresh();
    } catch(err) {
      toast("Trip scheduling failed: " + err.message, true);
    } finally {
      btn.disabled = false;
    }
  }

  function exportCsv() {
    const rows=[["Trip ID","Pickup","Dropoff","Pickup Window Start","Pickup Window End","Deadline","Distance km","Passengers","Status"],...state.trips.map(t=>[t.id,t.pickupLocationId,t.dropoffLocationId,t.pickupWindowStart,t.pickupWindowEnd,t.deadline,t.distanceKm,t.passengerCount,t.status||"PENDING"])];
    const csv=rows.map(row=>row.map(value=>{
      let cell=String(value??"");
      if (/^[=+\-@\t\r]/.test(cell)) cell="'"+cell;
      return `"${cell.replaceAll('"','""')}"`;
    }).join(",")).join("\r\n");
    const blob=new Blob([csv],{type:"text/csv;charset=utf-8;"}),url=URL.createObjectURL(blob),a=document.createElement("a");
    a.href=url;a.download="urjaroute-trip-report.csv";document.body.appendChild(a);a.click();a.remove();URL.revokeObjectURL(url);toast("Trip CSV exported.");
  }
  function openSettings(){ $("api-key-input").value=connection.key||""; $("api-base-input").value=connection.base||location.origin; $("settings-dialog").showModal(); }
  $("connection-button").addEventListener("click",openSettings); $("settings-button").addEventListener("click",openSettings);
  $("save-settings-button").addEventListener("click",e=>{e.preventDefault();saveConnection();$("settings-dialog").close();refresh();});
  $("refresh-button").addEventListener("click",refresh);
  ["hero-plan-button","quick-plan-button","studio-plan-button"].forEach(id=>$(id).addEventListener("click",generatePlan));
  $("simulate-drift-button").addEventListener("click",simulateDrift); $("simulate-failure-button").addEventListener("click",simulateFailure);
  $("export-button").addEventListener("click",exportCsv); $("trips-export-button").addEventListener("click",exportCsv);
  $("trip-search").addEventListener("input",renderTrips);

  if ($("open-register-vehicle-button")) {
    $("open-register-vehicle-button").addEventListener("click", () => $("register-vehicle-dialog").showModal());
  }
  if ($("register-vehicle-form")) {
    $("register-vehicle-form").addEventListener("submit", registerVehicleSubmit);
  }
  if ($("open-register-trip-button")) {
    $("open-register-trip-button").addEventListener("click", () => $("register-trip-dialog").showModal());
  }
  if ($("register-trip-form")) {
    $("register-trip-form").addEventListener("submit", registerTripSubmit);
  }

  document.querySelectorAll(".nav-item").forEach(a=>a.addEventListener("click",()=>{document.querySelectorAll(".nav-item").forEach(n=>n.classList.remove("active"));a.classList.add("active");$("breadcrumb-current").textContent=a.textContent.trim().replace(/\s+/g," ");}));
  $("today-label").textContent=new Intl.DateTimeFormat("en-IN",{weekday:"short",day:"2-digit",month:"short",timeZone:"Asia/Kolkata"}).format(new Date()).toUpperCase()+" · IST";
  setDemoState();
  refresh();
})();