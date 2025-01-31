package com.example.controller;

import com.example.domain.AuthInfo;
import com.example.domain.BasketballMatchInfo;
import com.example.domain.BetInfo;
import com.example.domain.FootballMatchInfo;
import com.example.service.GeneralBookieService;
import com.example.service.OddSpecialistService;
import org.apache.activemq.command.ActiveMQQueue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;

import javax.jms.Destination;
import javax.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
@author Qinyuan Zhang
@date 04/12/2018
*/
@Controller
public class GeneralBookieController {

   @Autowired
   private GeneralBookieService gbs;

   @Autowired
   private OddSpecialistService osl;

   /*
   The GET method to check bookie
    */
   public String checkBookie(Model model, HttpSession session, String bookie) {
       model.addAttribute("bookie", bookie);
       model.addAttribute("balance", gbs.getBalance(bookie));
      /*
      Notice that we need to send out and get info before the next step
      otherwise it will not work
      */

      Destination destination = new ActiveMQQueue("footballMatches.eventOrganiser");
      gbs.sendToGetEvents(destination, "football");
      Destination destination2 = new ActiveMQQueue("basketballMatches.eventOrganiser");
      gbs.sendToGetEvents(destination2, "basketball");
      return "bookie";
   }

   /*
   The GET method leads to the html file
   Notice that we want to use one single frontend template for
   all bookie companies and events
    */
   public String bookieGetBasketball(Model model, String bookie) {
       model.addAttribute("bookie", bookie);
       model.addAttribute("result","Basketball");

      List<BasketballMatchInfo> matchesList = gbs.getBasketballMatchesList();
      // Create a map to store all the matches info for particular event
      Map<String, String> currentMatchesMap = new HashMap<>();
      for (BasketballMatchInfo matchInfo : matchesList) {
         String displayOnHTML = matchInfo.getHomeTeam() + " (h) VS "
                 + matchInfo.getVisitingTeam();
         String hrefInfo = matchInfo.getHomeTeam() + "&"
                 + matchInfo.getVisitingTeam() + "&"
                 + matchInfo.getHomeTeamWinProb() + "&"
                 + matchInfo.getVisitingTeamWinProb();
         currentMatchesMap.put(displayOnHTML, hrefInfo);
      }
      model.addAttribute("matchesMap", currentMatchesMap);

      return "BetNow";
   }

   public String bookieCheckBasketballOdds(@PathVariable("hrefInfo") String hrefInfo, Model model, HttpSession session, String bookie, double calcNum) {
       model.addAttribute("bookie", bookie);
       model.addAttribute("result","Basketball");
      String[] hrefDetails = hrefInfo.split("\\&");
      List<Double> probabilities = new ArrayList<>();
      session.setAttribute("matchInfo", hrefDetails[0] + " VS " + hrefDetails[1]);
      for (int i = 2 ; i < 4 ; i++) probabilities.add(Double.valueOf(hrefDetails[i]));
      List<Double> odds = osl.generateFootballOdds(probabilities, calcNum);
      session.setAttribute("odds", odds);
      Map<String, Double> displayInfo =  new HashMap<>();
      displayInfo.put(hrefDetails[0] + " (h)", odds.get(0));
      displayInfo.put(hrefDetails[1], odds.get(1));
      model.addAttribute("oddsMap", displayInfo);
      return "CheckOdds";
   }

   public String bookiePlaceBetBasketball(@ModelAttribute(value = "betinfo") BetInfo betInfo, Model model,
                                               HttpSession session, String bookie) {
      model.addAttribute("bookie", bookie);
       // Does player have sufficient funds? This will determine the page rendered
      if(hasSufficientFunds(betInfo.getAmount(), gbs.getBalance(bookie))){
         session.setAttribute("sufficientFunds", true);
         gbs.updateBalance((AuthInfo)session.getAttribute("Auth"),bookie,-betInfo.getAmount());
      }
      else{
         session.setAttribute("sufficientFunds", false);
         return "Bets";
      }

      String matchInfo = (String) session.getAttribute("matchInfo");
      List<Double> odds= (List<Double>) session.getAttribute("odds");
      if (betInfo.getSelection().equals("null") || betInfo.getSelection().equals("tie") || matchInfo.equals("null"))   return "error";
      else {
         betInfo.setMatch(matchInfo);
         switch (betInfo.getSelection()) {
            case "home":
               betInfo.setOdd(odds.get(0));
               break;
            case "visiting":
               betInfo.setOdd(odds.get(1));
               break;
            default:
               betInfo.setOdd(0.0);
               break;
         }
         model.addAttribute("betsTable", gbs.placeBet(betInfo, bookie));
         model.addAttribute("result", "Your bet has been placed, here are all your bets.");
      }
      return "Bets";
   }

   /*
The GET method leads to the html file
Notice that we want to use one single frontend template for
all bookie companies and events
 */
   public String bookieGetFootball(Model model, String bookie) {
       model.addAttribute("bookie", bookie);
       model.addAttribute("result","Football");

      List<FootballMatchInfo> footballMatchesList = gbs.getFootballMatchesList();
      // Create a map to store all the matches info for particular event
      Map<String, String> currentMatchesMap = new HashMap<>();
      for (FootballMatchInfo matchInfo : footballMatchesList) {
            String displayOnHTML = "[" + matchInfo.getLeague() + "] "
                                    + matchInfo.getHomeTeam() + " (h) VS "
                                    + matchInfo.getVisitingTeam();
            String hrefInfo = matchInfo.getLeague() + "&"
                    + matchInfo.getHomeTeam() + "&"
                    + matchInfo.getVisitingTeam() + "&"
                    + matchInfo.getHomeTeamWinProb() + "&"
                    + matchInfo.getVisitingTeamWinProb() + "&"
                    + matchInfo.getDrawProb();
            currentMatchesMap.put(displayOnHTML, hrefInfo);

      }
      System.out.println("Controller get info: " + footballMatchesList.get(0).getHomeTeam());
      model.addAttribute("matchesMap", currentMatchesMap);
      return "BetNow";
   }

   public String bookieCheckFootballOdds(@PathVariable("hrefInfo") String hrefInfo, Model model, HttpSession session, String bookie, double calcNum) {
       model.addAttribute("bookie", bookie);
       model.addAttribute("result","Football");
      String[] hrefDetails = hrefInfo.split("\\&");
      List<Double> probabilities = new ArrayList<>();
      session.setAttribute("matchInfo", hrefDetails[0] + " " + hrefDetails[1] + " VS " + hrefDetails[2]);
      for (int i = 3 ; i < 6 ; i++) probabilities.add(Double.valueOf(hrefDetails[i]));
      List<Double> odds = osl.generateFootballOdds(probabilities, calcNum);
      session.setAttribute("odds", odds);
      Map<String, Double> displayInfo =  new HashMap<>();
      displayInfo.put(hrefDetails[1] + " (h)", odds.get(0));
      displayInfo.put(hrefDetails[2], odds.get(1));
      displayInfo.put("Draw", odds.get(2));
      model.addAttribute("oddsMap", displayInfo);
      return "CheckOdds";
   }

   public String bookiePlaceBetFootball(@ModelAttribute(value = "betinfo") BetInfo betInfo, Model model,
                                               HttpSession session, String bookie) {
      model.addAttribute("bookie", bookie);
      // Does player have sufficient funds? This will determine the page rendered
      if(hasSufficientFunds(betInfo.getAmount(), gbs.getBalance(bookie))){
         session.setAttribute("sufficientFunds", true);
         gbs.updateBalance((AuthInfo)session.getAttribute("Auth"),bookie,-betInfo.getAmount());
      }
      else{
         session.setAttribute("sufficientFunds", false);
         return "Bets";
      }

      String matchInfo = (String) session.getAttribute("matchInfo");
      List<Double> odds= (List<Double>) session.getAttribute("odds");
      if (betInfo.getSelection().equals("null") || matchInfo.equals("null"))   return "error";
      else {
         betInfo.setMatch(matchInfo);
         switch (betInfo.getSelection()) {
            case "home":
               betInfo.setOdd(odds.get(0));
               break;
            case "visiting":
               betInfo.setOdd(odds.get(1));
               break;
            case "tie":
               betInfo.setOdd(odds.get(2));
               break;
            default:
               betInfo.setOdd(0.0);
               break;
         }
         model.addAttribute("betsTable", gbs.placeBet(betInfo, bookie));
         model.addAttribute("result", "Your bet is placed, here are all your bets.");
      }
      return "Bets";
   }

   public String bookieViewBets(Model model, HttpSession session, String bookie) {
      model.addAttribute("bookie", bookie);
      model.addAttribute("betsTable", gbs.getBetInfoList(bookie));
      model.addAttribute("result", "Your bet is placed, here are all your bets.");
      session.setAttribute("sufficientFunds", true);
      return "Bets";
   }

   public boolean hasSufficientFunds(double betAmount , double balance) {
      return betAmount < balance;
   }
}
